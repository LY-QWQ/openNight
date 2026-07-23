package client.opennight.render;

import client.opennight.render.GlyphPage;

record Glyph(int u, int v, int width, int height, char value, GlyphPage owner) {
}