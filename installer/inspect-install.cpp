// Offline installation integrity and release-version inspection. No user data
// or network access: only three installed program files and our own manifest.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <bcrypt.h>
#include <cstdio>
#include <string>
#include <vector>

static const wchar_t* files[]={L"OML启动器.exe",L"使用说明.txt",L"卸载 OML.exe"};
static const wchar_t* legacyFiles[]={L"MXCloud启动器.exe",L"使用说明.txt",L"卸载 MXCloud.exe"};
static std::wstring join(const std::wstring& root,const wchar_t* leaf){return root+L"\\"+leaf;}
static std::wstring version(const std::wstring& file){
    DWORD ignored=0;DWORD size=GetFileVersionInfoSizeW(file.c_str(),&ignored);
    if(!size)return L"";
    std::vector<unsigned char> data(size);if(!GetFileVersionInfoW(file.c_str(),0,size,data.data()))return L"";
    VS_FIXEDFILEINFO* info=nullptr;UINT length=0;
    if(!VerQueryValueW(data.data(),L"\\",reinterpret_cast<void**>(&info),&length)||length<sizeof(*info)||info->dwSignature!=0xfeef04bd)return L"";
    return std::to_wstring(HIWORD(info->dwProductVersionMS))+L"."+std::to_wstring(LOWORD(info->dwProductVersionMS))+L"."+std::to_wstring(HIWORD(info->dwProductVersionLS));
}
static std::wstring digest(const std::wstring& file){
    HANDLE input=CreateFileW(file.c_str(),GENERIC_READ,FILE_SHARE_READ,nullptr,OPEN_EXISTING,FILE_FLAG_SEQUENTIAL_SCAN,nullptr);
    if(input==INVALID_HANDLE_VALUE)return L"";
    BCRYPT_ALG_HANDLE algorithm=nullptr;BCRYPT_HASH_HANDLE hash=nullptr;
    std::wstring result;
    if(BCryptOpenAlgorithmProvider(&algorithm,BCRYPT_SHA256_ALGORITHM,nullptr,0)>=0&&BCryptCreateHash(algorithm,&hash,nullptr,0,nullptr,0,0)>=0){
        unsigned char bytes[65536];DWORD count=0;bool okay=true;
        do{if(!ReadFile(input,bytes,sizeof(bytes),&count,nullptr)){okay=false;break;}
            if(count&&BCryptHashData(hash,bytes,count,0)<0){okay=false;break;}
        }while(count);
        unsigned char sum[32];
        if(okay&&BCryptFinishHash(hash,sum,sizeof(sum),0)>=0){
            const wchar_t* hex=L"0123456789abcdef";
            for(auto value:sum){result+=hex[value>>4];result+=hex[value&15];}
        }
    }
    if(hash)BCryptDestroyHash(hash);if(algorithm)BCryptCloseAlgorithmProvider(algorithm,0);CloseHandle(input);
    return result;
}
static std::wstring field(const std::wstring& manifest,const wchar_t* section,const wchar_t* key){
    wchar_t value[256]={};GetPrivateProfileStringW(section,key,L"",value,256,manifest.c_str());return value;
}
int wmain(int argc,wchar_t** argv){
    if(argc!=4)return 2;
    std::wstring mode=argv[1],root=argv[2],expected=argv[3],manifest=join(root,L".mx-install.ini");
    auto actual=version(join(root,files[0]));
    if(mode==L"write"){
        if(actual!=expected)return 3;
        std::vector<std::wstring> hashes;
        for(auto name:files){auto hash=digest(join(root,name));if(hash.empty())return 4;hashes.push_back(hash);}
        auto pending=manifest+L".tmp";DeleteFileW(pending.c_str());
        // Write a UTF-16 INI header so Chinese file names round-trip exactly.
        HANDLE output=CreateFileW(pending.c_str(),GENERIC_WRITE,0,nullptr,CREATE_NEW,FILE_ATTRIBUTE_HIDDEN,nullptr);
        if(output==INVALID_HANDLE_VALUE)return 5;
        wchar_t bom=0xfeff;DWORD written=0;bool okay=WriteFile(output,&bom,2,&written,nullptr)&&written==2;CloseHandle(output);
        okay=okay&&WritePrivateProfileStringW(L"Install",L"Version",expected.c_str(),pending.c_str());
        for(size_t i=0;i<hashes.size()&&okay;i++)okay=!!WritePrivateProfileStringW(L"Files",files[i],hashes[i].c_str(),pending.c_str());
        WritePrivateProfileStringW(nullptr,nullptr,nullptr,pending.c_str());
        if(!okay||!MoveFileExW(pending.c_str(),manifest.c_str(),MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH)){DeleteFileW(pending.c_str());return 6;}
        fputs("written",stdout);return 0;
    }
    if(mode!=L"inspect")return 2;
    const wchar_t** inspected=files;
    if(GetFileAttributesW(join(root,files[0]).c_str())==INVALID_FILE_ATTRIBUTES&&
       GetFileAttributesW(join(root,legacyFiles[0]).c_str())!=INVALID_FILE_ATTRIBUTES){
        inspected=legacyFiles;actual=version(join(root,inspected[0]));
    }
    bool found=GetFileAttributesW(manifest.c_str())!=INVALID_FILE_ATTRIBUTES;
    for(int i=0;i<3;i++)found=found||GetFileAttributesW(join(root,inspected[i]).c_str())!=INVALID_FILE_ATTRIBUTES;
    if(!found){fputs("new",stdout);return 0;}
    auto recorded=field(manifest,L"Install",L"Version");
    bool complete=!actual.empty()&&actual==recorded;
    for(int i=0;i<3;i++){auto name=inspected[i];auto sum=digest(join(root,name));complete=complete&&!sum.empty()&&sum==field(manifest,L"Files",name);}
    if(!complete)fputs("repair",stdout);else fputs(actual==expected?"same":"update",stdout);
    return 0;
}
