package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HexFormat;

import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * M63 persistence format for the EP-aware KP-KP compact domain.
 *
 * M86 changes ONLY the in-memory runtime representation.  The on-disk format,
 * version, metadata, state numbering, and SHA-256 identity remain exactly M63.
 * Existing validated "*-canonical-ep.ftb.gz" assets therefore do not need to
 * be rebuilt or migrated.
 *
 * Historical runtime representation:
 *
 *     byte[stateCount]  outcome   ~= 32.1 MiB
 *     short[stateCount] distance  ~= 64.2 MiB
 *
 * M86 runtime representation:
 *
 *     byte[stateCount] packed     ~= 32.1 MiB
 *
 * Each byte stores WDL + DTM. KP-KP's validated maximum DTM is 66, while this
 * encoding supports DTM values through 125. INVALID / UNKNOWN / DRAW use
 * dedicated codes. WIN and LOSS each use one contiguous DTM range.
 */
public final class FourPieceTierTwoKpkpTablebaseCodec {

    public static final String RUNTIME_BUILD_ID =
            "M86-KPKP-PACKED-RUNTIME-V1";

    private static final int MAGIC =
            0x4B504B50; // "KPKP"

    /*
     * IMPORTANT: the persistence version stays 1. M86 deliberately does not
     * alter the persisted M63 asset format.
     */
    private static final int VERSION =
            1;

    private static final int SHA_256_BYTES =
            32;

    private static final int STREAM_BUFFER_BYTES =
            16 * 1024;

    /*
     * Packed runtime codes, interpreted unsigned (0..255).
     *
     *   0       INVALID
     *   1       UNKNOWN
     *   2       DRAW
     *   3..128  WIN  with DTM 0..125
     *   129..254 LOSS with DTM 0..125
     *   255     reserved / invalid encoding
     */
    private static final int PACK_INVALID = 0;
    private static final int PACK_UNKNOWN = 1;
    private static final int PACK_DRAW = 2;
    private static final int PACK_WIN_BASE = 3;
    private static final int MAX_PACKED_DISTANCE = 125;
    private static final int PACK_LOSS_BASE =
            PACK_WIN_BASE + MAX_PACKED_DISTANCE + 1;

    private FourPieceTierTwoKpkpTablebaseCodec() {
    }


    // =========================================================
    // Persistence API
    // =========================================================

    public static void save(
            Tablebase tablebase,
            Path path
    ) throws IOException {

        if (tablebase == null
                || path == null) {

            throw new IllegalArgumentException(
                    "KP-KP tablebase/path cannot be null."
            );
        }

        Path absolute =
                path.toAbsolutePath()
                        .normalize();

        Path parent =
                absolute.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp =
                absolute.resolveSibling(
                        absolute.getFileName()
                                + ".tmp"
                );

        Files.deleteIfExists(temp);

        try {
            writeFile(tablebase, temp);

            try {
                Files.move(
                        temp,
                        absolute,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temp,
                        absolute,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException
                 | RuntimeException exception) {

            Files.deleteIfExists(temp);
            throw exception;
        }
    }


    /**
     * Load the unchanged M63 gzip asset directly into M86's packed runtime
     * representation.
     *
     * The file stores all outcomes first and all short distances second. To
     * avoid ever holding a second state-sized array, the packed array first
     * temporarily contains the raw outcome byte for each state. As each DTM is
     * streamed later, that same byte is replaced in-place by the packed WDL/DTM
     * code.
     */
    public static Tablebase load(
            Path path
    ) throws IOException {

        if (path == null) {
            throw new IllegalArgumentException(
                    "KP-KP input path cannot be null."
            );
        }

        Path absolute =
                path.toAbsolutePath()
                        .normalize();

        try (
                InputStream file =
                        Files.newInputStream(absolute);

                BufferedInputStream buffered =
                        new BufferedInputStream(
                                file,
                                1 << 20
                        );

                GZIPInputStream gzip =
                        new GZIPInputStream(
                                buffered,
                                1 << 20
                        );

                DataInputStream input =
                        new DataInputStream(gzip)
        ) {

            if (input.readInt() != MAGIC) {
                throw new IOException(
                        "Not an EP-aware KP-KP tablebase: "
                                + absolute
                );
            }

            int version =
                    input.readInt();

            if (version != VERSION) {
                throw new IOException(
                        "Unsupported KP-KP tablebase version "
                                + version
                );
            }

            FourPieceMaterialClass.Distribution distribution;

            PieceType first;
            PieceType second;

            try {
                distribution =
                        FourPieceMaterialClass.Distribution.valueOf(
                                input.readUTF()
                        );

                first =
                        PieceType.valueOf(
                                input.readUTF()
                        );

                second =
                        PieceType.valueOf(
                                input.readUTF()
                        );

            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Invalid KP-KP material metadata.",
                        exception
                );
            }

            FourPieceMaterialClass material =
                    switch (distribution) {
                        case SAME_SIDE ->
                                FourPieceMaterialClass.sameSide(
                                        first,
                                        second
                                );

                        case SPLIT ->
                                FourPieceMaterialClass.split(
                                        first,
                                        second
                                );
                    };

            if (!material.equals(
                    FourPieceMaterialClass.split(
                            PieceType.PAWN,
                            PieceType.PAWN
                    )
            )) {
                throw new IOException(
                        "KP-KP asset contains wrong material: "
                                + material.displayName()
                );
            }

            int stateCount =
                    input.readInt();

            if (stateCount
                    != FourPieceTierTwoKpkpStateIndex.STATE_COUNT) {

                throw new IOException(
                        "KP-KP state count "
                                + stateCount
                                + " != runtime "
                                + FourPieceTierTwoKpkpStateIndex.STATE_COUNT
                );
            }

            long legalStates =
                    input.readLong();

            long legalEpStates =
                    input.readLong();

            long wins =
                    input.readLong();

            long losses =
                    input.readLong();

            long draws =
                    input.readLong();

            int maximumDistance =
                    input.readInt();

            validateCounts(
                    legalStates,
                    legalEpStates,
                    wins,
                    losses,
                    draws
            );

            if (maximumDistance < 0
                    || maximumDistance > MAX_PACKED_DISTANCE) {

                throw new IOException(
                        "KP-KP maximum DTM "
                                + maximumDistance
                                + " exceeds M86 packed runtime limit "
                                + MAX_PACKED_DISTANCE
                                + "."
                );
            }

            /*
             * This is the only state-sized allocation performed by runtime
             * load. During the first file section it temporarily holds raw
             * outcome bytes. During the second section those bytes are packed
             * in-place together with their exact distances.
             */
            byte[] packed =
                    new byte[stateCount];

            MessageDigest digest =
                    sha256();

            input.readFully(packed);
            digest.update(packed);

            byte[] digestBuffer =
                    new byte[STREAM_BUFFER_BYTES];

            int digestIndex =
                    0;

            for (int state = 0;
                 state < stateCount;
                 state++) {

                short distance =
                        input.readShort();

                digestBuffer[digestIndex++] =
                        (byte) (distance >>> 8);

                digestBuffer[digestIndex++] =
                        (byte) distance;

                if (digestIndex
                        == digestBuffer.length) {

                    digest.update(digestBuffer);
                    digestIndex = 0;
                }

                byte rawOutcome =
                        packed[state];

                packed[state] =
                        pack(
                                rawOutcome,
                                distance
                        );
            }

            if (digestIndex > 0) {
                digest.update(
                        digestBuffer,
                        0,
                        digestIndex
                );
            }

            byte[] expected =
                    new byte[SHA_256_BYTES];

            try {
                input.readFully(expected);

            } catch (EOFException exception) {
                throw new IOException(
                        "KP-KP asset ended before checksum.",
                        exception
                );
            }

            byte[] actual =
                    digest.digest();

            if (!MessageDigest.isEqual(
                    expected,
                    actual
            )) {
                throw new IOException(
                        "KP-KP checksum mismatch. Expected "
                                + HexFormat.of()
                                .formatHex(expected)
                                + ", computed "
                                + HexFormat.of()
                                .formatHex(actual)
                );
            }

            if (input.read() != -1) {
                throw new IOException(
                        "Unexpected trailing KP-KP data."
                );
            }

            return Tablebase.packed(
                    packed,
                    legalStates,
                    legalEpStates,
                    wins,
                    losses,
                    draws,
                    maximumDistance
            );
        }
    }


    // =========================================================
    // Content identity
    // =========================================================

    public static String contentDigest(
            Tablebase tablebase
    ) {

        if (tablebase == null) {
            throw new IllegalArgumentException(
                    "KP-KP tablebase cannot be null."
            );
        }

        MessageDigest digest =
                sha256();

        updateDigestWithOutcomes(
                digest,
                tablebase
        );

        updateDigestWithDistances(
                digest,
                tablebase
        );

        return HexFormat.of()
                .formatHex(
                        digest.digest()
                );
    }

    public static String contentDigest(
            FourPieceTierTwoKpkpRetrogradeBuilder.Result result
    ) {

        if (result == null) {
            throw new IllegalArgumentException(
                    "KP-KP result cannot be null."
            );
        }

        return digest(
                result.outcome(),
                result.distance()
        );
    }

    public static Tablebase fromResult(
            FourPieceTierTwoKpkpRetrogradeBuilder.Result result
    ) {

        if (result == null) {
            throw new IllegalArgumentException(
                    "KP-KP result cannot be null."
            );
        }

        return new Tablebase(
                result.outcome(),
                result.distance(),
                result.legalStates(),
                result.legalEpStates(),
                result.wins(),
                result.losses(),
                result.draws(),
                result.maximumDistance()
        );
    }


    // =========================================================
    // Unchanged M63 file writer
    // =========================================================

    private static void writeFile(
            Tablebase tablebase,
            Path path
    ) throws IOException {

        try (
                OutputStream file =
                        Files.newOutputStream(path);

                BufferedOutputStream buffered =
                        new BufferedOutputStream(
                                file,
                                1 << 20
                        );

                GZIPOutputStream gzip =
                        new GZIPOutputStream(
                                buffered,
                                1 << 20
                        );

                DataOutputStream output =
                        new DataOutputStream(gzip)
        ) {

            output.writeInt(MAGIC);
            output.writeInt(VERSION);

            FourPieceMaterialClass material =
                    FourPieceMaterialClass.split(
                            PieceType.PAWN,
                            PieceType.PAWN
                    );

            output.writeUTF(
                    material.distribution()
                            .name()
            );

            output.writeUTF(
                    material.firstType()
                            .name()
            );

            output.writeUTF(
                    material.secondType()
                            .name()
            );

            output.writeInt(
                    FourPieceTierTwoKpkpStateIndex.STATE_COUNT
            );

            output.writeLong(
                    tablebase.legalStates()
            );

            output.writeLong(
                    tablebase.legalEpStates()
            );

            output.writeLong(
                    tablebase.wins()
            );

            output.writeLong(
                    tablebase.losses()
            );

            output.writeLong(
                    tablebase.draws()
            );

            output.writeInt(
                    tablebase.maximumDistance()
            );

            MessageDigest digest =
                    sha256();

            byte[] buffer =
                    new byte[STREAM_BUFFER_BYTES];

            /* Write the original M63 outcome section. */
            int index = 0;

            for (int state = 0;
                 state < tablebase.stateCount();
                 state++) {

                buffer[index++] =
                        tablebase.outcome(state);

                if (index == buffer.length) {
                    output.write(buffer);
                    digest.update(buffer);
                    index = 0;
                }
            }

            if (index > 0) {
                output.write(
                        buffer,
                        0,
                        index
                );

                digest.update(
                        buffer,
                        0,
                        index
                );
            }

            /* Write the original M63 big-endian short distance section. */
            index = 0;

            for (int state = 0;
                 state < tablebase.stateCount();
                 state++) {

                short distance =
                        tablebase.distance(state);

                buffer[index++] =
                        (byte) (distance >>> 8);

                buffer[index++] =
                        (byte) distance;

                if (index == buffer.length) {
                    output.write(buffer);
                    digest.update(buffer);
                    index = 0;
                }
            }

            if (index > 0) {
                output.write(
                        buffer,
                        0,
                        index
                );

                digest.update(
                        buffer,
                        0,
                        index
                );
            }

            output.write(
                    digest.digest()
            );
        }
    }


    // =========================================================
    // Digest helpers
    // =========================================================

    private static void updateDigestWithOutcomes(
            MessageDigest digest,
            Tablebase tablebase
    ) {

        byte[] buffer =
                new byte[STREAM_BUFFER_BYTES];

        int index = 0;

        for (int state = 0;
             state < tablebase.stateCount();
             state++) {

            buffer[index++] =
                    tablebase.outcome(state);

            if (index == buffer.length) {
                digest.update(buffer);
                index = 0;
            }
        }

        if (index > 0) {
            digest.update(
                    buffer,
                    0,
                    index
            );
        }
    }

    private static void updateDigestWithDistances(
            MessageDigest digest,
            Tablebase tablebase
    ) {

        byte[] buffer =
                new byte[STREAM_BUFFER_BYTES];

        int index = 0;

        for (int state = 0;
             state < tablebase.stateCount();
             state++) {

            short value =
                    tablebase.distance(state);

            buffer[index++] =
                    (byte) (value >>> 8);

            buffer[index++] =
                    (byte) value;

            if (index == buffer.length) {
                digest.update(buffer);
                index = 0;
            }
        }

        if (index > 0) {
            digest.update(
                    buffer,
                    0,
                    index
            );
        }
    }

    private static String digest(
            byte[] outcome,
            short[] distance
    ) {

        MessageDigest digest =
                sha256();

        digest.update(outcome);

        byte[] buffer =
                new byte[STREAM_BUFFER_BYTES];

        int index =
                0;

        for (short value :
                distance) {

            buffer[index++] =
                    (byte) (value >>> 8);

            buffer[index++] =
                    (byte) value;

            if (index
                    == buffer.length) {

                digest.update(buffer);
                index = 0;
            }
        }

        if (index > 0) {
            digest.update(
                    buffer,
                    0,
                    index
            );
        }

        return HexFormat.of()
                .formatHex(
                        digest.digest()
                );
    }

    private static MessageDigest sha256() {

        try {
            return MessageDigest.getInstance(
                    "SHA-256"
            );

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable.",
                    exception
            );
        }
    }


    // =========================================================
    // Packed WDL / DTM encoding
    // =========================================================

    private static byte pack(
            byte outcome,
            short distance
    ) throws IOException {

        if (outcome
                == FourPieceTablebase.INVALID) {

            return (byte) PACK_INVALID;
        }

        if (outcome
                == FourPieceTablebase.UNKNOWN) {

            return (byte) PACK_UNKNOWN;
        }

        if (outcome
                == FourPieceTablebase.DRAW) {

            return (byte) PACK_DRAW;
        }

        if (outcome
                != FourPieceTablebase.WIN
                && outcome
                != FourPieceTablebase.LOSS) {

            throw new IOException(
                    "Unknown KP-KP outcome byte: "
                            + outcome
            );
        }

        if (distance < 0
                || distance > MAX_PACKED_DISTANCE) {

            throw new IOException(
                    "KP-KP decisive state DTM "
                            + distance
                            + " cannot be packed."
            );
        }

        int code =
                (outcome == FourPieceTablebase.WIN
                        ? PACK_WIN_BASE
                        : PACK_LOSS_BASE)
                        + distance;

        return (byte) code;
    }

    private static byte unpackOutcome(
            byte packed
    ) {

        int code =
                packed & 0xFF;

        if (code == PACK_INVALID) {
            return FourPieceTablebase.INVALID;
        }

        if (code == PACK_UNKNOWN) {
            return FourPieceTablebase.UNKNOWN;
        }

        if (code == PACK_DRAW) {
            return FourPieceTablebase.DRAW;
        }

        if (code >= PACK_WIN_BASE
                && code < PACK_LOSS_BASE) {

            return FourPieceTablebase.WIN;
        }

        if (code >= PACK_LOSS_BASE
                && code <= PACK_LOSS_BASE
                + MAX_PACKED_DISTANCE) {

            return FourPieceTablebase.LOSS;
        }

        throw new IllegalStateException(
                "Invalid packed KP-KP runtime code: "
                        + code
        );
    }

    private static short unpackDistance(
            byte packed
    ) {

        int code =
                packed & 0xFF;

        if (code >= PACK_WIN_BASE
                && code < PACK_LOSS_BASE) {

            return (short) (
                    code
                            - PACK_WIN_BASE
            );
        }

        if (code >= PACK_LOSS_BASE
                && code <= PACK_LOSS_BASE
                + MAX_PACKED_DISTANCE) {

            return (short) (
                    code
                            - PACK_LOSS_BASE
            );
        }

        if (code == PACK_INVALID
                || code == PACK_UNKNOWN
                || code == PACK_DRAW) {

            return -1;
        }

        throw new IllegalStateException(
                "Invalid packed KP-KP runtime code: "
                        + code
        );
    }


    // =========================================================
    // Metadata validation
    // =========================================================

    private static void validateCounts(
            long legalStates,
            long legalEpStates,
            long wins,
            long losses,
            long draws
    ) throws IOException {

        if (legalStates < 0
                || legalEpStates < 0
                || legalEpStates > legalStates
                || wins < 0
                || losses < 0
                || draws < 0
                || wins + losses + draws
                != legalStates) {

            throw new IOException(
                    "Invalid KP-KP tablebase counts."
            );
        }
    }


    // =========================================================
    // Runtime container
    // =========================================================

    public static final class Tablebase {

        /*
         * Offline/build-backed representation. Existing builder APIs can keep
         * handing this class their result arrays unchanged.
         */
        private final byte[] outcome;
        private final short[] distance;

        /*
         * M86 runtime-backed representation. load(...) populates only this
         * array, so probing a persisted KP-KP asset retains one byte/state.
         */
        private final byte[] packed;

        private final long legalStates;
        private final long legalEpStates;
        private final long wins;
        private final long losses;
        private final long draws;

        private final int maximumDistance;

        public Tablebase(
                byte[] outcome,
                short[] distance,
                long legalStates,
                long legalEpStates,
                long wins,
                long losses,
                long draws,
                int maximumDistance
        ) {

            if (outcome == null
                    || distance == null
                    || outcome.length
                    != FourPieceTierTwoKpkpStateIndex.STATE_COUNT
                    || distance.length
                    != FourPieceTierTwoKpkpStateIndex.STATE_COUNT) {

                throw new IllegalArgumentException(
                        "Incorrect KP-KP tablebase arrays."
                );
            }

            validateTablebaseMetadata(
                    legalStates,
                    legalEpStates,
                    wins,
                    losses,
                    draws,
                    maximumDistance
            );

            this.outcome = outcome;
            this.distance = distance;
            this.packed = null;

            this.legalStates = legalStates;
            this.legalEpStates = legalEpStates;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.maximumDistance = maximumDistance;
        }

        private Tablebase(
                byte[] packed,
                long legalStates,
                long legalEpStates,
                long wins,
                long losses,
                long draws,
                int maximumDistance
        ) {

            if (packed == null
                    || packed.length
                    != FourPieceTierTwoKpkpStateIndex.STATE_COUNT) {

                throw new IllegalArgumentException(
                        "Incorrect packed KP-KP runtime array."
                );
            }

            validateTablebaseMetadata(
                    legalStates,
                    legalEpStates,
                    wins,
                    losses,
                    draws,
                    maximumDistance
            );

            this.outcome = null;
            this.distance = null;
            this.packed = packed;

            this.legalStates = legalStates;
            this.legalEpStates = legalEpStates;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.maximumDistance = maximumDistance;
        }

        private static Tablebase packed(
                byte[] packed,
                long legalStates,
                long legalEpStates,
                long wins,
                long losses,
                long draws,
                int maximumDistance
        ) {

            return new Tablebase(
                    packed,
                    legalStates,
                    legalEpStates,
                    wins,
                    losses,
                    draws,
                    maximumDistance
            );
        }

        public byte outcome(
                int state
        ) {

            checkState(state);

            if (packed != null) {
                return unpackOutcome(
                        packed[state]
                );
            }

            return outcome[state];
        }

        public short distance(
                int state
        ) {

            checkState(state);

            if (packed != null) {
                return unpackDistance(
                        packed[state]
                );
            }

            return distance[state];
        }

        public int stateCount() {
            return FourPieceTierTwoKpkpStateIndex.STATE_COUNT;
        }

        public long legalStates() {
            return legalStates;
        }

        public long legalEpStates() {
            return legalEpStates;
        }

        public long wins() {
            return wins;
        }

        public long losses() {
            return losses;
        }

        public long draws() {
            return draws;
        }

        public int maximumDistance() {
            return maximumDistance;
        }

        /**
         * True for tablebases loaded from persistence through the M86 runtime
         * path. False for the large offline builder container.
         */
        public boolean isPackedRuntime() {
            return packed != null;
        }

        /**
         * Raw state-storage bytes retained by this Tablebase instance. This is
         * intentionally deterministic and excludes ordinary object headers.
         */
        public long runtimeStorageBytes() {

            if (packed != null) {
                return packed.length;
            }

            return (long) outcome.length
                    + 2L * distance.length;
        }

        private void checkState(
                int state
        ) {

            if (state < 0
                    || state >= stateCount()) {

                throw new IndexOutOfBoundsException(
                        "KP-KP state out of range: "
                                + state
                );
            }
        }

        private static void validateTablebaseMetadata(
                long legalStates,
                long legalEpStates,
                long wins,
                long losses,
                long draws,
                int maximumDistance
        ) {

            if (legalStates < 0
                    || legalEpStates < 0
                    || legalEpStates > legalStates
                    || wins < 0
                    || losses < 0
                    || draws < 0
                    || wins + losses + draws
                    != legalStates) {

                throw new IllegalArgumentException(
                        "KP-KP WDL counts do not balance."
                );
            }

            if (maximumDistance < 0) {
                throw new IllegalArgumentException(
                        "KP-KP maximum DTM cannot be negative."
                );
            }
        }
    }
}
