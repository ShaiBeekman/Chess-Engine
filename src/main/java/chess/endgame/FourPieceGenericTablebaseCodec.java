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
 * Persistent codec for generic four-piece tablebases.
 *
 * File format:
 *
 *     magic
 *     version
 *
 *     material distribution
 *     first extra type
 *     second extra type
 *     same-side-owner-is-white
 *
 *     primitive state count
 *
 *     legal count
 *     win count
 *     loss count
 *     draw count
 *     maximum DTM
 *
 *     outcome[stateCount]
 *     distance[stateCount]
 *
 *     SHA-256(outcome || big-endian-distance)
 *
 *
 * The entire stream is GZIP-compressed.
 */
public final class FourPieceGenericTablebaseCodec {

    private static final int MAGIC =
            0x34475442; // "4GTB"

    private static final int VERSION =
            1;

    private static final int SHA_256_BYTES =
            32;


    private FourPieceGenericTablebaseCodec() {

    }


    public static void save(
            FourPieceGenericTablebase tablebase,
            Path path
    ) throws IOException {

        if (tablebase == null) {

            throw new IllegalArgumentException(
                    "Tablebase cannot be null."
            );
        }


        if (path == null) {

            throw new IllegalArgumentException(
                    "Output path cannot be null."
            );
        }


        Path absolute =
                path.toAbsolutePath()
                        .normalize();


        Path parent =
                absolute.getParent();


        if (parent != null) {

            Files.createDirectories(
                    parent
            );
        }


        Path temp =
                absolute.resolveSibling(
                        absolute.getFileName()
                                + ".tmp"
                );


        Files.deleteIfExists(
                temp
        );


        try {

            writeFile(
                    tablebase,
                    temp
            );


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

            Files.deleteIfExists(
                    temp
            );

            throw exception;
        }
    }


    public static FourPieceGenericTablebase load(
            Path path
    ) throws IOException {

        if (path == null) {

            throw new IllegalArgumentException(
                    "Input path cannot be null."
            );
        }


        Path absolute =
                path.toAbsolutePath()
                        .normalize();


        try (
                InputStream file =
                        Files.newInputStream(
                                absolute
                        );

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
                        new DataInputStream(
                                gzip
                        )
        ) {

            int magic =
                    input.readInt();


            if (magic != MAGIC) {

                throw new IOException(
                        "Not a generic four-piece tablebase: "
                                + absolute
                );
            }


            int version =
                    input.readInt();


            if (version != VERSION) {

                throw new IOException(
                        "Unsupported four-piece tablebase version "
                                + version
                                + ". Expected "
                                + VERSION
                                + "."
                );
            }


            FourPieceMaterialClass.Distribution distribution;

            PieceType firstType;

            PieceType secondType;


            try {

                distribution =
                        FourPieceMaterialClass.Distribution.valueOf(
                                input.readUTF()
                        );

                firstType =
                        PieceType.valueOf(
                                input.readUTF()
                        );

                secondType =
                        PieceType.valueOf(
                                input.readUTF()
                        );

            } catch (IllegalArgumentException exception) {

                throw new IOException(
                        "Invalid material metadata in tablebase.",
                        exception
                );
            }


            FourPieceMaterialClass material =
                    switch (distribution) {

                        case SAME_SIDE ->
                                FourPieceMaterialClass.sameSide(
                                        firstType,
                                        secondType
                                );

                        case SPLIT ->
                                FourPieceMaterialClass.split(
                                        firstType,
                                        secondType
                                );
                    };


            boolean sameSideOwnerIsWhite =
                    input.readBoolean();


            int stateCount =
                    input.readInt();


            if (stateCount
                    != FourPieceGenericPrimitiveState.STATE_COUNT) {

                throw new IOException(
                        "Tablebase state count "
                                + stateCount
                                + " does not match runtime state count "
                                + FourPieceGenericPrimitiveState.STATE_COUNT
                                + "."
                );
            }


            long legalStates =
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
                    wins,
                    losses,
                    draws
            );


            byte[] outcome =
                    new byte[
                            stateCount
                            ];


            short[] distance =
                    new short[
                            stateCount
                            ];


            MessageDigest digest =
                    sha256();


            input.readFully(
                    outcome
            );


            digest.update(
                    outcome
            );


            byte[] distanceDigestBuffer =
                    new byte[
                            16 * 1024
                            ];


            int digestIndex =
                    0;


            for (int state = 0;
                 state < stateCount;
                 state++) {

                short value =
                        input.readShort();


                distance[state] =
                        value;


                distanceDigestBuffer[digestIndex++] =
                        (byte) (
                                (value >>> 8)
                                        & 0xFF
                        );

                distanceDigestBuffer[digestIndex++] =
                        (byte) (
                                value
                                        & 0xFF
                        );


                if (digestIndex
                        == distanceDigestBuffer.length) {

                    digest.update(
                            distanceDigestBuffer
                    );

                    digestIndex =
                            0;
                }
            }


            if (digestIndex > 0) {

                digest.update(
                        distanceDigestBuffer,
                        0,
                        digestIndex
                );
            }


            byte[] expectedDigest =
                    new byte[
                            SHA_256_BYTES
                            ];


            try {

                input.readFully(
                        expectedDigest
                );

            } catch (EOFException exception) {

                throw new IOException(
                        "Tablebase ended before its checksum.",
                        exception
                );
            }


            byte[] actualDigest =
                    digest.digest();


            if (!MessageDigest.isEqual(
                    expectedDigest,
                    actualDigest
            )) {

                throw new IOException(
                        "Four-piece tablebase checksum mismatch. "
                                + "Expected "
                                + HexFormat.of()
                                .formatHex(
                                        expectedDigest
                                )
                                + ", computed "
                                + HexFormat.of()
                                .formatHex(
                                        actualDigest
                                )
                                + "."
                );
            }


            /*
             * There should be no additional decompressed bytes.
             */
            if (input.read()
                    != -1) {

                throw new IOException(
                        "Unexpected trailing data in tablebase file."
                );
            }


            return new FourPieceGenericTablebase(
                    material,
                    sameSideOwnerIsWhite,
                    outcome,
                    distance,
                    legalStates,
                    wins,
                    losses,
                    draws,
                    maximumDistance
            );
        }
    }


    public static String contentDigest(
            FourPieceGenericTablebase tablebase
    ) {

        if (tablebase == null) {

            throw new IllegalArgumentException(
                    "Tablebase cannot be null."
            );
        }


        MessageDigest digest =
                sha256();


        byte[] outcome =
                tablebase.outcomeData();

        short[] distance =
                tablebase.distanceData();


        digest.update(
                outcome
        );


        byte[] buffer =
                new byte[
                        16 * 1024
                        ];


        int index =
                0;


        for (short value :
                distance) {

            buffer[index++] =
                    (byte) (
                            (value >>> 8)
                                    & 0xFF
                    );

            buffer[index++] =
                    (byte) (
                            value
                                    & 0xFF
                    );


            if (index
                    == buffer.length) {

                digest.update(
                        buffer
                );

                index =
                        0;
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


    private static void writeFile(
            FourPieceGenericTablebase tablebase,
            Path path
    ) throws IOException {

        try (
                OutputStream file =
                        Files.newOutputStream(
                                path
                        );

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
                        new DataOutputStream(
                                gzip
                        )
        ) {

            output.writeInt(
                    MAGIC
            );

            output.writeInt(
                    VERSION
            );


            output.writeUTF(
                    tablebase.material()
                            .distribution()
                            .name()
            );

            output.writeUTF(
                    tablebase.material()
                            .firstType()
                            .name()
            );

            output.writeUTF(
                    tablebase.material()
                            .secondType()
                            .name()
            );


            output.writeBoolean(
                    tablebase.sameSideOwnerIsWhite()
            );


            output.writeInt(
                    FourPieceGenericPrimitiveState.STATE_COUNT
            );


            output.writeLong(
                    tablebase.legalStates()
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


            byte[] outcome =
                    tablebase.outcomeData();

            short[] distance =
                    tablebase.distanceData();


            MessageDigest digest =
                    sha256();


            output.write(
                    outcome
            );


            digest.update(
                    outcome
            );


            byte[] digestBuffer =
                    new byte[
                            16 * 1024
                            ];


            int digestIndex =
                    0;


            for (short value :
                    distance) {

                output.writeShort(
                        value
                );


                digestBuffer[digestIndex++] =
                        (byte) (
                                (value >>> 8)
                                        & 0xFF
                        );

                digestBuffer[digestIndex++] =
                        (byte) (
                                value
                                        & 0xFF
                        );


                if (digestIndex
                        == digestBuffer.length) {

                    digest.update(
                            digestBuffer
                    );

                    digestIndex =
                            0;
                }
            }


            if (digestIndex > 0) {

                digest.update(
                        digestBuffer,
                        0,
                        digestIndex
                );
            }


            output.write(
                    digest.digest()
            );
        }
    }


    private static void validateCounts(
            long legalStates,
            long wins,
            long losses,
            long draws
    ) throws IOException {

        if (legalStates < 0
                || wins < 0
                || losses < 0
                || draws < 0) {

            throw new IOException(
                    "Negative tablebase metadata count."
            );
        }


        if (wins + losses + draws
                != legalStates) {

            throw new IOException(
                    "Corrupt tablebase metadata: "
                            + "WIN + LOSS + DRAW != legal states."
            );
        }
    }


    private static MessageDigest sha256() {

        try {

            return MessageDigest.getInstance(
                    "SHA-256"
            );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }
}