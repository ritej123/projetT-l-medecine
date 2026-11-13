package com.telemed.platform.auth.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

import com.google.code.kaptcha.text.WordRenderer;

public class CaptchaWordRenderer implements WordRenderer {

    private static final Color[] PALETTE = {
        new Color(20, 72, 111),
        new Color(111, 54, 102),
        new Color(126, 54, 45),
        new Color(31, 99, 73),
        new Color(130, 77, 24),
        new Color(44, 66, 131)
    };
    private static final String[] FONTS = {"Arial", "Courier New", "Georgia", "Verdana"};
    private final SecureRandom random = new SecureRandom();

    @Override
    public BufferedImage renderWord(String word, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(248, 250, 249));
            graphics.fillRect(0, 0, width, height);
            drawNoise(graphics, width, height);

            char[] characters = word.toCharArray();
            int left = 18;
            int step = (width - 36) / Math.max(1, characters.length - 1);
            for (int index = 0; index < characters.length; index++) {
                int fontSize = 29 + random.nextInt(10);
                Font font = new Font(FONTS[random.nextInt(FONTS.length)], Font.BOLD, fontSize);
                graphics.setFont(font);
                Rectangle2D bounds = font.getStringBounds(String.valueOf(characters[index]), graphics.getFontRenderContext());
                float x = left + index * step - (float) bounds.getWidth() / 2;
                float y = 41 + random.nextInt(16);
                double rotation = Math.toRadians(random.nextInt(51) - 25);
                graphics.setColor(PALETTE[random.nextInt(PALETTE.length)]);
                graphics.rotate(rotation, x + (float) bounds.getWidth() / 2, y - fontSize / 2f);
                graphics.drawString(String.valueOf(characters[index]), x, y);
                graphics.rotate(-rotation, x + (float) bounds.getWidth() / 2, y - fontSize / 2f);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void drawNoise(Graphics2D graphics, int width, int height) {
        for (int index = 0; index < 70; index++) {
            graphics.setColor(new Color(87, 111, 108, 65));
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            graphics.fillOval(x, y, 1 + random.nextInt(2), 1 + random.nextInt(2));
        }
        for (int index = 0, lines = 3 + random.nextInt(3); index < lines; index++) {
            Path2D curve = new Path2D.Float();
            curve.moveTo(0, random.nextInt(height));
            curve.curveTo(
                    width / 3.0, random.nextInt(height),
                    width * 2.0 / 3.0, random.nextInt(height),
                    width, random.nextInt(height));
            Color color = PALETTE[random.nextInt(PALETTE.length)];
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 115));
            graphics.setStroke(new BasicStroke(1.0f + random.nextFloat()));
            graphics.draw(curve);
        }
    }
}
