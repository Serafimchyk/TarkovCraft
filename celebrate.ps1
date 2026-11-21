param([string]$BaseDir = ".")

$code = @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Threading;
using System.Windows.Forms;

public class TarkovFireworksForm : Form {
    private class Rocket {
        public float X, Y, Vx, Vy, TargetY;
        public Color Col;
    }

    private class Spark {
        public float X, Y, PrevX, PrevY, Vx, Vy, Life, MaxLife, Size;
        public Color Col;
    }

    private readonly List<Rocket> rockets = new List<Rocket>();
    private readonly List<Spark> sparks = new List<Spark>();
    private readonly Random rng = new Random();
    private readonly System.Windows.Forms.Timer timer;
    private readonly Color[] palette = new Color[] {
        Color.FromArgb(212, 180, 106), // Tarkov Gold
        Color.FromArgb(119, 184, 90),  // Extraction Green
        Color.FromArgb(255, 145, 65),  // Flare Orange
        Color.FromArgb(99, 210, 255),  // Armor Cyan
        Color.FromArgb(255, 90, 90),   // Tracer Red
        Color.FromArgb(245, 240, 225)  // Warm White
    };

    private Image bannerImg;
    private int frame = 0;
    private const int MAX_FRAMES = 440; // ~7 seconds

    public TarkovFireworksForm(string baseDir) {
        this.FormBorderStyle = FormBorderStyle.None;
        this.WindowState = FormWindowState.Maximized;
        this.TopMost = true;
        this.DoubleBuffered = true;
        this.KeyPreview = true;
        this.BackColor = Color.FromArgb(10, 12, 15);
        this.Opacity = 0.93;
        this.Cursor = Cursors.Hand;

        string[] candidates = new string[] {
            Path.Combine(baseDir, "success.png"),
            Path.Combine(baseDir, "success.jpg"),
            Path.Combine(baseDir, "success.jpeg")
        };
        foreach (string p in candidates) {
            if (File.Exists(p)) {
                try { bannerImg = Image.FromFile(p); break; } catch { }
            }
        }

        this.MouseDown += delegate { this.Close(); };
        this.KeyDown += delegate { this.Close(); };

        // Start fanfare async so it doesn't lag the animation
        ThreadPool.QueueUserWorkItem(delegate {
            try {
                Console.Beep(523, 80);
                Console.Beep(659, 80);
                Console.Beep(784, 90);
                Console.Beep(1046, 220);
            } catch { }
        });

        this.Shown += delegate {
            for (int i = 0; i < 4; i++) SpawnRocket(true);
        };

        timer = new System.Windows.Forms.Timer();
        timer.Interval = 16;
        timer.Tick += OnTick;
        timer.Start();
    }

    private void SpawnRocket(bool immediateBurst) {
        int w = Math.Max(400, this.ClientSize.Width);
        int h = Math.Max(300, this.ClientSize.Height);
        Color col = palette[rng.Next(palette.Length)];

        if (immediateBurst) {
            float bx = (float)(w * (0.12 + rng.NextDouble() * 0.76));
            float by = (float)(h * (0.12 + rng.NextDouble() * 0.45));
            Explode(bx, by, col);
            return;
        }

        Rocket r = new Rocket();
        r.X = (float)(w * (0.1 + rng.NextDouble() * 0.8));
        r.Y = h + 10;
        r.Vx = (float)((rng.NextDouble() - 0.5) * 4.5);
        r.Vy = -(float)(13.0 + rng.NextDouble() * 6.0);
        r.TargetY = (float)(h * (0.12 + rng.NextDouble() * 0.42));
        r.Col = col;
        rockets.Add(r);
    }

    private void Explode(float x, float y, Color baseCol) {
        int count = 55 + rng.Next(35);
        for (int i = 0; i < count; i++) {
            double angle = rng.NextDouble() * Math.PI * 2.0;
            double speed = 1.8 + rng.NextDouble() * 8.5;
            Spark s = new Spark();
            s.X = x;
            s.Y = y;
            s.PrevX = x;
            s.PrevY = y;
            s.Vx = (float)(Math.Cos(angle) * speed);
            s.Vy = (float)(Math.Sin(angle) * speed);
            s.MaxLife = 38 + rng.Next(32);
            s.Life = s.MaxLife;
            s.Size = (float)(2.0 + rng.NextDouble() * 2.8);
            s.Col = (rng.Next(4) == 0) ? Color.FromArgb(255, 248, 225) : baseCol;
            sparks.Add(s);
        }
    }

    private void OnTick(object sender, EventArgs e) {
        frame++;
        if (frame >= MAX_FRAMES) {
            this.Close();
            return;
        }
        if (frame % 14 == 0 && frame < MAX_FRAMES - 60) {
            SpawnRocket(false);
        }

        for (int i = rockets.Count - 1; i >= 0; i--) {
            Rocket r = rockets[i];
            r.X += r.Vx;
            r.Y += r.Vy;
            r.Vy += 0.16f;
            if (r.Y <= r.TargetY || r.Vy >= -1.0f) {
                Explode(r.X, r.Y, r.Col);
                rockets.RemoveAt(i);
            }
        }

        for (int i = sparks.Count - 1; i >= 0; i--) {
            Spark s = sparks[i];
            s.PrevX = s.X;
            s.PrevY = s.Y;
            s.X += s.Vx;
            s.Y += s.Vy;
            s.Vx *= 0.965f;
            s.Vy = s.Vy * 0.965f + 0.12f;
            s.Life -= 1.0f;
            if (s.Life <= 0) {
                sparks.RemoveAt(i);
            }
        }

        this.Invalidate();
    }

    protected override void OnPaint(PaintEventArgs e) {
        Graphics g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        int w = this.ClientSize.Width;
        int h = this.ClientSize.Height;

        // 1. Fireworks rockets & glowing trails
        foreach (Rocket r in rockets) {
            using (Pen p = new Pen(Color.FromArgb(220, r.Col), 3.0f)) {
                g.DrawLine(p, r.X, r.Y, r.X - r.Vx * 2.5f, r.Y - r.Vy * 2.5f);
            }
        }

        foreach (Spark s in sparks) {
            int alpha = Math.Max(0, Math.Min(255, (int)(255.0f * (s.Life / s.MaxLife))));
            using (Pen trail = new Pen(Color.FromArgb(alpha, s.Col), s.Size * 0.75f)) {
                g.DrawLine(trail, s.PrevX, s.PrevY, s.X, s.Y);
            }
            using (SolidBrush dot = new SolidBrush(Color.FromArgb(alpha, s.Col))) {
                g.FillEllipse(dot, s.X - s.Size * 0.5f, s.Y - s.Size * 0.5f, s.Size, s.Size);
            }
        }

        // 2. Center Tarkov HUD Card (with optional banner image)
        Color gold = Color.FromArgb(212, 180, 106);
        Color green = Color.FromArgb(119, 184, 90);
        StringFormat sf = new StringFormat();
        sf.Alignment = StringAlignment.Center;
        sf.LineAlignment = StringAlignment.Center;

        int cardW = bannerImg != null ? Math.Min(w - 120, 760) : 620;
        int imgH = bannerImg != null ? (int)(cardW * 9.0 / 16.0) : 0;
        int cardH = bannerImg != null ? (imgH + 46) : 210;
        int cx = (w - cardW) / 2;
        int cy = (h - cardH) / 2;

        using (SolidBrush bg = new SolidBrush(Color.FromArgb(225, 13, 15, 19)))
        using (Pen border = new Pen(gold, 2.0f)) {
            g.FillRectangle(bg, cx, cy, cardW, cardH);
            g.DrawRectangle(border, cx, cy, cardW, cardH);
        }

        if (bannerImg != null) {
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.DrawImage(bannerImg, cx + 6, cy + 6, cardW - 12, imgH - 6);
            using (Font fHint = new Font("Consolas", 10.5f, FontStyle.Bold))
            using (SolidBrush bMuted = new SolidBrush(Color.FromArgb(170, 210, 205, 195))) {
                g.DrawString("CLICK ANYWHERE OR PRESS ANY KEY TO CLOSE", fHint, bMuted,
                    new RectangleF(cx, cy + imgH + 6, cardW, 24), sf);
            }
        } else {
            using (SolidBrush hdrBg = new SolidBrush(Color.FromArgb(235, 22, 26, 32))) {
                g.FillRectangle(hdrBg, cx + 2, cy + 2, cardW - 4, 46);
            }
            using (Font fTitle = new Font("Consolas", 20f, FontStyle.Bold))
            using (Font fSub = new Font("Consolas", 13f, FontStyle.Bold))
            using (Font fSmall = new Font("Consolas", 10f, FontStyle.Regular))
            using (SolidBrush bGold = new SolidBrush(gold))
            using (SolidBrush bGreen = new SolidBrush(green))
            using (SolidBrush bWhite = new SolidBrush(Color.FromArgb(235, 232, 224)))
            using (SolidBrush bMuted = new SolidBrush(Color.FromArgb(145, 152, 162))) {
                g.DrawString("EXTRACTION COMPLETE // BUILD SUCCESSFUL", fTitle, bGold,
                    new RectangleF(cx, cy + 4, cardW, 44), sf);

                using (SolidBrush pill = new SolidBrush(Color.FromArgb(45, 119, 184, 90)))
                using (Pen pillBorder = new Pen(green, 1.5f)) {
                    Rectangle pillRect = new Rectangle(cx + 48, cy + 68, cardW - 96, 36);
                    g.FillRectangle(pill, pillRect);
                    g.DrawRectangle(pillBorder, pillRect);
                    g.DrawString("+ DEPLOYED: tarkovcraft-0.1.0.jar", fSub, bGreen, pillRect, sf);
                }

                g.DrawString("MODRINTH PROFILE: [ test ]", fSub, bWhite,
                    new RectangleF(cx, cy + 118, cardW, 28), sf);
                g.DrawString("CLICK ANYWHERE OR PRESS ANY KEY TO CLOSE", fSmall, bMuted,
                    new RectangleF(cx, cy + 158, cardW, 24), sf);
            }
        }

        // Bottom auto-close progress bar
        float remain = Math.Max(0.0f, 1.0f - (frame / (float)MAX_FRAMES));
        int barW = (int)((cardW - 4) * remain);
        using (SolidBrush bProg = new SolidBrush(gold)) {
            g.FillRectangle(bProg, cx + 2, cy + cardH - 6, barW, 4);
        }
        sf.Dispose();
    }

    protected override void OnFormClosed(FormClosedEventArgs e) {
        if (timer != null) { timer.Stop(); timer.Dispose(); }
        if (bannerImg != null) { bannerImg.Dispose(); }
        base.OnFormClosed(e);
    }
}
'@

Add-Type -ReferencedAssemblies "System.Windows.Forms", "System.Drawing" -TypeDefinition $code
[System.Windows.Forms.Application]::EnableVisualStyles()
[System.Windows.Forms.Application]::Run((New-Object TarkovFireworksForm($BaseDir)))