package dev.astraterra.casino;

final class ResponsiveCasinoLayout {
    enum Mode { LARGE, MEDIUM, COMPACT }
    enum Page { TABLE, WALLET, JOURNAL }

    record Box(int x, int y, int w, int h) {
        int right() { return x + w; }
        int bottom() { return y + h; }
        Box inset(int pad) { return new Box(x + pad, y + pad, Math.max(0, w - pad * 2), Math.max(0, h - pad * 2)); }
        boolean contains(Box other) {
            return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
        }
        boolean intersects(Box other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    record Layout(
        Mode mode,
        Box frame,
        Box header,
        Box navigation,
        Box wallet,
        Box table,
        Box journal,
        Box footer,
        boolean showWallet,
        boolean showTable,
        boolean showJournal
    ) {}

    private ResponsiveCasinoLayout() {}

    static Layout calculate(int screenWidth, int screenHeight, Page compactPage, Page mediumSidePage) {
        int margin = clamp(Math.min(screenWidth, screenHeight) / 70, 5, 13);
        int frameW = Math.max(320, screenWidth - margin * 2);
        int frameH = Math.max(240, screenHeight - margin * 2);
        Box frame = new Box((screenWidth - frameW) / 2, (screenHeight - frameH) / 2, frameW, frameH);

        Mode mode;
        if (screenWidth >= 1180 && screenHeight >= 620) mode = Mode.LARGE;
        else if (screenWidth >= 760 && screenHeight >= 500) mode = Mode.MEDIUM;
        else mode = Mode.COMPACT;

        int headerH = mode == Mode.COMPACT ? 50 : 58;
        int navH = mode == Mode.LARGE ? 0 : 26;
        int footerH = mode == Mode.COMPACT ? 31 : 35;
        int gap = mode == Mode.COMPACT ? 5 : 8;
        int innerX = frame.x + gap;
        int innerW = frame.w - gap * 2;
        Box header = new Box(frame.x + 2, frame.y + 2, frame.w - 4, headerH - 4);
        Box navigation = navH == 0 ? new Box(0, 0, 0, 0) : new Box(innerX, frame.y + headerH, innerW, navH - 2);
        int contentY = frame.y + headerH + navH;
        int contentH = frame.h - headerH - navH - footerH - gap;
        Box footer = new Box(innerX, frame.bottom() - footerH, innerW, footerH - gap / 2);

        if (mode == Mode.LARGE) {
            int leftW = clamp((int) Math.round(innerW * 0.22), 230, 330);
            int rightW = clamp((int) Math.round(innerW * 0.24), 250, 390);
            int centerW = innerW - leftW - rightW - gap * 2;
            if (centerW < 520) {
                int missing = 520 - centerW;
                int takeLeft = Math.min(missing / 2, Math.max(0, leftW - 205));
                leftW -= takeLeft;
                missing -= takeLeft;
                rightW -= Math.min(missing, Math.max(0, rightW - 220));
                centerW = innerW - leftW - rightW - gap * 2;
            }
            Box wallet = new Box(innerX, contentY, leftW, contentH);
            Box table = new Box(wallet.right() + gap, contentY, centerW, contentH);
            Box journal = new Box(table.right() + gap, contentY, rightW, contentH);
            return new Layout(mode, frame, header, navigation, wallet, table, journal, footer, true, true, true);
        }

        if (mode == Mode.MEDIUM) {
            int sideW = clamp((int) Math.round(innerW * 0.29), 220, 300);
            int tableW = innerW - sideW - gap;
            Box table = new Box(innerX, contentY, tableW, contentH);
            Box side = new Box(table.right() + gap, contentY, sideW, contentH);
            boolean walletSide = mediumSidePage != Page.JOURNAL;
            Box wallet = walletSide ? side : new Box(0, 0, 0, 0);
            Box journal = walletSide ? new Box(0, 0, 0, 0) : side;
            return new Layout(mode, frame, header, navigation, wallet, table, journal, footer, walletSide, true, !walletSide);
        }

        Box page = new Box(innerX, contentY, innerW, contentH);
        Box wallet = compactPage == Page.WALLET ? page : new Box(0, 0, 0, 0);
        Box table = compactPage == Page.TABLE ? page : new Box(0, 0, 0, 0);
        Box journal = compactPage == Page.JOURNAL ? page : new Box(0, 0, 0, 0);
        return new Layout(mode, frame, header, navigation, wallet, table, journal, footer,
            compactPage == Page.WALLET, compactPage == Page.TABLE, compactPage == Page.JOURNAL);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
