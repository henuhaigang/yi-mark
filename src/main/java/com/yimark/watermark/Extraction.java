package com.yimark.watermark;

/** Result of reading the invisible watermark out of an image. */
public final class Extraction {

    public final boolean recovered;
    public final byte[] payload;
    public final double syncScore;
    public final int codewords;
    public final double votesPerBit;
    public final String transform;
    public final String reason;

    private Extraction(boolean recovered, byte[] payload, double syncScore, int codewords,
                       double votesPerBit, String transform, String reason) {
        this.recovered = recovered;
        this.payload = payload;
        this.syncScore = syncScore;
        this.codewords = codewords;
        this.votesPerBit = votesPerBit;
        this.transform = transform;
        this.reason = reason;
    }

    public static Extraction success(byte[] payload, double syncScore, int codewords,
                                     double votesPerBit, String transform) {
        return new Extraction(true, payload, syncScore, codewords, votesPerBit, transform, null);
    }

    public static Extraction failure(double syncScore, int codewords,
                                     String transform, String reason) {
        return new Extraction(false, null, syncScore, codewords, 0, transform, reason);
    }

    public boolean recovered() {
        return recovered;
    }

    public byte[] payload() {
        return payload;
    }

    public double syncScore() {
        return syncScore;
    }

    public int codewords() {
        return codewords;
    }

    public double votesPerBit() {
        return votesPerBit;
    }

    public String transform() {
        return transform;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(recovered ? "recovered" : "not recovered");
        sb.append(" (sync ").append(String.format("%.1f%%", syncScore * 100));
        sb.append(", ").append(codewords).append(" codeword(s)");
        if (votesPerBit > 0) {
            sb.append(", ").append(String.format("%.1f", votesPerBit)).append(" votes/bit");
        }
        sb.append(", transform: ").append(transform);
        if (reason != null) {
            sb.append(") - ").append(reason);
        } else {
            sb.append(")");
        }
        return sb.toString();
    }
}
