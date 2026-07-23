package client.opennight.render;

import client.opennight.render.Paint.LinearGradient;

public final class GradientFactory {
    public static Paint.LinearGradient buildLinearGradient(float[] stops, float angle) {
        return new Paint.LinearGradient(stops, angle);
    }
}