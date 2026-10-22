package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 皮肤玩偶（桌宠）。
 *
 * <p>用纯 Java2D 画一个盒式人物模型：头部、身体、四肢各六个面按 Minecraft 皮肤的 UV
 * 布局取图，外层（帽子 / 外套 / 袖子 / 裤子）向外鼓 0.6 像素叠在上面，正版账号再挂一层
 * 披风。左键拖动移动位置，右键或按住 Shift 拖动旋转，滚轮缩放，双击复位。
 */
final class SkinDoll extends JComponent {

    interface Listener {
        /** 位置 / 角度 / 缩放变化后回调，用于持久化。 */
        void changed();
    }

    private static final int TEX = 64;
    private static final double MODEL_HEIGHT = 32;   // 头 8 + 身体 12 + 腿 12

    private final Path home;
    private final Listener listener;
    private Session session;

    private BufferedImage skin;
    private BufferedImage cape;
    private BufferedImage texture;
    private boolean slim;

    private float yaw = 28f, pitch = 8f;
    private double zoom = 1.0;
    private double idlePhase;

    private Point press;
    private Point locationStart;
    private float yawStart, pitchStart;
    private boolean rotating, moving;

    private final Timer idle;
    private volatile boolean disposed;

    SkinDoll(Session session, Path home, Listener listener) {
        this.session = session;
        this.home = home;
        this.listener = listener;
        setName("skinDoll");
        setOpaque(false);
        setPreferredSize(new Dimension(150, 210));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("拖动移动 · 右键或 Shift 拖动旋转 · 滚轮缩放 · 双击复位");
        installMouse();
        idle = new Timer(40, e -> {
            if (!isShowing()) return;
            idlePhase += 0.045;
            repaint();
        });
        idle.start();
        reload(session);
    }

    // ============================================================ 数据加载

    /** 换账号或改皮肤后重新加载：正版取官方皮肤 + 披风，离线取导入皮肤或官方默认。 */
    void reload(Session next) {
        this.session = next;
        final Session target = next;
        Thread worker = new Thread(() -> {
            BufferedImage loadedSkin = null;
            BufferedImage loadedCape = null;
            try {
                loadedSkin = SkinFetcher.fullSkin(target, home);
                loadedCape = SkinFetcher.cape(target, home);
            } catch (Throwable ignored) {
            }
            final BufferedImage skinImage = loadedSkin;
            final BufferedImage capeImage = loadedCape;
            SwingUtilities.invokeLater(() -> {
                if (disposed || session != target) return;
                skin = skinImage;
                cape = capeImage;
                texture = normalize(skin);
                slim = SkinFetcher.slim(skin);
                repaint();
            });
        }, "oml-skin-doll");
        worker.setDaemon(true);
        worker.start();
    }

    /** 统一成 64×64 纹理（64×32 老皮肤按原比例铺进上半部分）。 */
    private static BufferedImage normalize(BufferedImage source) {
        BufferedImage target = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
        if (source == null) return target;
        Graphics2D g = target.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, TEX, TEX);
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int height = source.getHeight() * TEX / Math.max(1, source.getWidth());
        g.drawImage(source, 0, 0, TEX, Math.min(TEX, height), null);
        g.dispose();
        return target;
    }

    void stop() {
        disposed = true;
        idle.stop();
    }

    // ============================================================ 状态

    float yaw() { return yaw; }

    float pitch() { return pitch; }

    double zoom() { return zoom; }

    void restore(float yaw, float pitch, double zoom) {
        this.yaw = yaw;
        this.pitch = Math.max(-40f, Math.min(40f, pitch));
        this.zoom = Math.max(0.75, Math.min(1.5, zoom));
        repaint();
    }

    void resetPose() {
        yaw = 28f;
        pitch = 8f;
        zoom = 1.0;
        repaint();
    }

    // ============================================================ 交互

    private void installMouse() {
        MouseAdapter handler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Container parent = getParent();
                if (parent instanceof JLayeredPane layered) layered.moveToFront(SkinDoll.this);
                press = e.getPoint();
                locationStart = getLocation();
                boolean rotate = SwingUtilities.isRightMouseButton(e) || e.isShiftDown();
                if (rotate) {
                    rotating = true;
                    yawStart = yaw;
                    pitchStart = pitch;
                } else {
                    moving = true;
                }
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (press == null) return;
                int dx = e.getX() - press.x;
                int dy = e.getY() - press.y;
                if (rotating) {
                    yaw = yawStart + dx * 0.6f;
                    pitch = Math.max(-40f, Math.min(40f, pitchStart + dy * 0.4f));
                    repaint();
                } else if (moving && locationStart != null) {
                    Container parent = getParent();
                    int maxX = parent == null ? Integer.MAX_VALUE : Math.max(0, parent.getWidth() - getWidth());
                    int maxY = parent == null ? Integer.MAX_VALUE : Math.max(0, parent.getHeight() - getHeight());
                    int x = Math.max(-getWidth() / 3, Math.min(maxX, locationStart.x + dx));
                    int y = Math.max(0, Math.min(maxY, locationStart.y + dy));
                    setLocation(x, y);
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                boolean changed = rotating || moving;
                rotating = false;
                moving = false;
                press = null;
                if (changed && listener != null) listener.changed();
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    resetPose();
                    if (listener != null) listener.changed();
                }
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                zoom = Math.max(0.75, Math.min(1.5, zoom - e.getWheelRotation() * 0.06));
                repaint();
                if (listener != null) listener.changed();
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
    }

    // ============================================================ 模型

    /** 一个面：屏幕坐标四点 + 纹理矩形 + 明暗 + 是否来自披风纹理。 */
    private record Face(double[][] points, double nx, double ny, double nz, int u, int v, int w, int h, double shade, boolean cape) {}

    private List<Face> model() {
        List<Face> faces = new ArrayList<>();
        boolean overlay = skin != null && skin.getHeight() >= 64 && skin.getWidth() >= 64;
        int armWidth = slim ? 3 : 4;
        double armX = slim ? 5.6 : 6.2;

        // 内层（y=0 为脚底）：左腿 / 右腿 / 身体 / 双臂 / 头
        box(faces, -2.05, 6, 0, 3.95, 12, 4, 0, 16, 4, 4, 12);
        box(faces, 2.05, 6, 0, 3.95, 12, 4, overlay ? 16 : 0, overlay ? 48 : 16, 4, 4, 12);
        box(faces, 0, 18, 0, 8, 12, 4, 16, 16, 8, 4, 12);
        box(faces, -armX, 18, 0, armWidth, 12, 4, 40, 16, armWidth, 4, 12);
        box(faces, armX, 18, 0, armWidth, 12, 4, overlay ? 32 : 40, overlay ? 48 : 16, armWidth, 4, 12);
        box(faces, 0, 28, 0, 8, 8, 8, 0, 0, 8, 8, 8);

        if (overlay) {
            // 外层：帽子 / 外套 / 袖子 / 裤子，向外鼓 0.6 像素再叠一层
            box(faces, 0, 28, 0, 8.6, 8.6, 8.6, 32, 0, 8, 8, 8);
            box(faces, 0, 18, 0, 8.6, 12.3, 4.6, 16, 32, 8, 4, 12);
            box(faces, -armX, 18, 0, armWidth + 0.6, 12.3, 4.6, 40, 32, armWidth, 4, 12);
            box(faces, armX, 18, 0, armWidth + 0.6, 12.3, 4.6, 48, 48, armWidth, 4, 12);
            box(faces, -2.05, 6, 0, 4.4, 12.3, 4.6, 0, 32, 4, 4, 12);
            box(faces, 2.05, 6, 0, 4.4, 12.3, 4.6, 0, 48, 4, 4, 12);
        }
        if (cape != null) {
            // 披风贴在后背，10×16×1，离身体留一点缝
            box(faces, 0, 17, -3.2, 10, 16, 1, 1, 1, 10, 1, 16, true);
        }
        return faces;
    }
    private void box(List<Face> out, double cx, double cy, double cz,
                     double sx, double sy, double sz, int rx, int ry, int tw, int td, int th) {
        box(out, cx, cy, cz, sx, sy, sz, rx, ry, tw, td, th, false);
    }

    /**
     * 按皮肤 UV 布局添加盒子。
     *
     * @param cx,cy,cz 中心（y 向上）
     * @param sx,sy,sz 绘制尺寸
     * @param rx,ry    部件在皮肤图上的 16×16 区域左上角
     * @param tw,td,th 纹理上的宽 / 厚 / 高（像素）
     */
    private void box(List<Face> out, double cx, double cy, double cz,
                     double sx, double sy, double sz, int rx, int ry, int tw, int td, int th, boolean capeFace) {
        double hx = sx / 2, hy = sy / 2, hz = sz / 2;

        out.add(new Face(new double[][]{
                {cx - hx, cy + hy, cz + hz}, {cx + hx, cy + hy, cz + hz},
                {cx + hx, cy + hy, cz - hz}, {cx - hx, cy + hy, cz - hz}},
                0, 1, 0, rx + td, ry, tw, td, shade(0, 1, 0), capeFace));                      // 顶
        out.add(new Face(new double[][]{
                {cx - hx, cy - hy, cz - hz}, {cx + hx, cy - hy, cz - hz},
                {cx + hx, cy - hy, cz + hz}, {cx - hx, cy - hy, cz + hz}},
                0, -1, 0, rx + td + tw, ry, tw, td, shade(0, -1, 0), capeFace));               // 底
        out.add(new Face(new double[][]{
                {cx + hx, cy + hy, cz + hz}, {cx + hx, cy + hy, cz - hz},
                {cx + hx, cy - hy, cz - hz}, {cx + hx, cy - hy, cz + hz}},
                1, 0, 0, rx, ry + td, td, th, shade(1, 0, 0), capeFace));                      // 右
        out.add(new Face(new double[][]{
                {cx - hx, cy + hy, cz + hz}, {cx + hx, cy + hy, cz + hz},
                {cx + hx, cy - hy, cz + hz}, {cx - hx, cy - hy, cz + hz}},
                0, 0, 1, rx + td, ry + td, tw, th, shade(0, 0, 1), capeFace));                 // 前
        out.add(new Face(new double[][]{
                {cx - hx, cy + hy, cz - hz}, {cx - hx, cy + hy, cz + hz},
                {cx - hx, cy - hy, cz + hz}, {cx - hx, cy - hy, cz - hz}},
                -1, 0, 0, rx + td + tw, ry + td, td, th, shade(-1, 0, 0), capeFace));          // 左
        out.add(new Face(new double[][]{
                {cx + hx, cy + hy, cz - hz}, {cx - hx, cy + hy, cz - hz},
                {cx - hx, cy - hy, cz - hz}, {cx + hx, cy - hy, cz - hz}},
                0, 0, -1, rx + td + tw + td, ry + td, tw, th, shade(0, 0, -1), capeFace));     // 后
    }
    /** 定向光：上方偏前左亮，背面暗。 */
    private static double shade(double nx, double ny, double nz) {
        double lx = -0.30, ly = 0.55, lz = 0.78;
        double length = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double dot = (nx * lx + ny * ly + nz * lz) / length;
        // 环境光占大头，避免侧后方部件死黑
        return 0.80 + 0.20 * Math.max(0, dot);
    }

    // ============================================================ 绘制

    private record Projected(Face face, double depth) {}

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int width = getWidth(), height = getHeight();
            if (width <= 0 || height <= 0) return;

            if (texture == null || skin == null) {
                g.setColor(UiTheme.p().muted());
                g.setFont(UiTheme.font(11, false));
                g.drawString("正在加载皮肤…", 12, height - 12);
                return;
            }

            // 模型坐标：脚底 y=0，头顶 y=32，x/z 以身体中线为 0。
            // 先算出旋转 + 俯仰后的真实包围盒，再等比缩放并居中，保证整个人都画得下。
            double sway = Math.sin(idlePhase) * 2.6;
            double yawRad = Math.toRadians(yaw + sway);
            double pitchRad = Math.toRadians(pitch);
            double cosYaw = Math.cos(yawRad), sinYaw = Math.sin(yawRad);
            double cosPitch = Math.cos(pitchRad), sinPitch = Math.sin(pitchRad);

            List<double[]> world = new ArrayList<>();
            List<Face> faces = model();
            for (Face face : faces) {
                for (double[] point : face.points()) {
                    double x = point[0], y = point[1], z = point[2];
                    double x1 = x * cosYaw + z * sinYaw;
                    double z1 = -x * sinYaw + z * cosYaw;
                    double y1 = y * cosPitch - z1 * sinPitch;
                    double z2 = y * sinPitch + z1 * cosPitch;
                    world.add(new double[]{x1, y1, z2});
                }
            }
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (double[] p : world) {
                minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
            }
            double modelW = Math.max(0.001, maxX - minX);
            double modelH = Math.max(0.001, maxY - minY);
            double padding = 0.88;
            double scale = Math.min(width * padding / modelW, height * padding / modelH) * zoom;
            double anchorX = width / 2.0 - (minX + maxX) / 2.0 * scale;
            double anchorY = height / 2.0 + (minY + maxY) / 2.0 * scale;

            // 地面投影
            g.setColor(new Color(0, 0, 0, 42));
            double feetY = anchorY - minY * scale;
            g.fill(new Ellipse2D.Double(width * 0.26, feetY - height * 0.03,
                    width * 0.48, height * 0.045));

            List<Projected> projected = new ArrayList<>();
            int index = 0;
            for (Face face : faces) {
                double[][] screen = new double[4][2];
                double depth = 0;
                for (int i = 0; i < 4; i++) {
                    double[] p = world.get(index++);
                    screen[i][0] = anchorX + p[0] * scale;
                    screen[i][1] = anchorY - p[1] * scale;
                    depth += p[2];
                }
                projected.add(new Projected(
                        new Face(screen, face.nx(), face.ny(), face.nz(),
                                face.u(), face.v(), face.w(), face.h(), face.shade(), face.cape()),
                        depth / 4));
            }
            // 画家算法：远的先画
            projected.sort(Comparator.comparingDouble(Projected::depth));
            for (Projected item : projected) {
                Face face = item.face();
                drawFace(g, face, face.cape() ? cape : texture);
            }
        } finally {
            g.dispose();
        }
    }
    private void drawFace(Graphics2D g, Face face, BufferedImage source) {
        if (source == null) return;
        double[][] p = face.points();
        Path2D path = new Path2D.Double();
        path.moveTo(p[0][0], p[0][1]);
        path.lineTo(p[1][0], p[1][1]);
        path.lineTo(p[2][0], p[2][1]);
        path.lineTo(p[3][0], p[3][1]);
        path.closePath();

        Shape clipped = g.getClip();
        g.clip(path);
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1];
        double bx = p[3][0] - p[0][0], by = p[3][1] - p[0][1];
        AffineTransform transform = new AffineTransform();
        transform.translate(p[0][0], p[0][1]);
        transform.concatenate(new AffineTransform(ax, ay, bx, by, 0, 0));
        transform.scale(1.0 / Math.max(1, face.w()), 1.0 / Math.max(1, face.h()));
        transform.translate(-face.u(), -face.v());
        g.drawImage(source, transform, null);
        g.setClip(clipped);

        if (face.shade() < 0.995) {
            g.setColor(new Color(0, 0, 0, (int) Math.round(255 * (1 - face.shade()))));
            g.fill(path);
        }
    }
}
