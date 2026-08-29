package com.volmit.shapedportals.geometry;

public record GridPoint(int horizontal, int vertical) {
    public GridPoint up() {
        return new GridPoint(horizontal, vertical + 1);
    }

    public GridPoint down() {
        return new GridPoint(horizontal, vertical - 1);
    }

    public GridPoint left() {
        return new GridPoint(horizontal - 1, vertical);
    }

    public GridPoint right() {
        return new GridPoint(horizontal + 1, vertical);
    }
}
