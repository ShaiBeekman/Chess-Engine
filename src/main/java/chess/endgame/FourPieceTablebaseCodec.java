package main.java.chess.endgame;

import main.java.chess.model.Color;
import main.java.chess.model.PieceType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Binary persistence codec for exact four-piece tablebases.
 *
 * Format v1:
 *
 *     int   magic       = "CTB4"
 *     short version     = 1
 *     byte  material    = 1 KQRK
 *     byte  strongColor = 0 white, 1 black
 *     int   stateCount
 *     byte[stateCount]  outcome
 *     short[stateCount] distance
 */
public final class FourPieceTablebaseCodec {

    private static final int MAGIC =
            0x43544234; // CTB4

    private static final short VERSION =
            1;

    private static final byte MATERIAL_KQRK =
            1;

    private static final byte COLOR_WHITE =
            0;

    private static final byte COLOR_BLACK =
            1;


    private FourPieceTablebaseCodec() {
    }


    public static void save(
            Path path,
            FourPieceTablebase tablebase
    ) throws IOException {

        if (path == null) {

            throw new IllegalArgumentException(
                    "Tablebase path cannot be null."
            );
        }


        if (tablebase == null) {

            throw new IllegalArgumentException(
                    "Tablebase cannot be null."
            );
        }


        if (!tablebase.isBuilt()) {

            throw new IllegalStateException(
                    "Tablebase must be built or restored before it can be saved."
            );
        }


        Path parent =
                path.toAbsolutePath()
                        .getParent();


        if (parent != null) {

            Files.createDirectories(
                    parent
            );
        }


        try (OutputStream raw =
                     Files.newOutputStream(
                             path
                     );

             BufferedOutputStream buffered =
                     new BufferedOutputStream(
                             raw
                     );

             DataOutputStream output =
                     new DataOutputStream(
                             buffered
                     )) {

            write(
                    output,
                    tablebase
            );
        }
    }


    public static FourPieceTablebase load(
            Path path
    ) throws IOException {

        if (path == null) {

            throw new IllegalArgumentException(
                    "Tablebase path cannot be null."
            );
        }


        try (InputStream input =
                     Files.newInputStream(
                             path
                     )) {

            return load(
                    input
            );
        }
    }


    public static FourPieceTablebase load(
            InputStream input
    ) throws IOException {

        if (input == null) {

            throw new IllegalArgumentException(
                    "Tablebase input stream cannot be null."
            );
        }


        DataInputStream data =
                input instanceof DataInputStream existing
                        ? existing
                        : new DataInputStream(
                        new BufferedInputStream(
                                input
                        )
                );


        try {

            int magic =
                    data.readInt();


            if (magic != MAGIC) {

                throw new IOException(
                        "Invalid four-piece tablebase magic."
                );
            }


            short version =
                    data.readShort();


            if (version != VERSION) {

                throw new IOException(
                        "Unsupported four-piece tablebase version: "
                                + version
                                + "."
                );
            }


            byte materialCode =
                    data.readByte();


            if (materialCode
                    != MATERIAL_KQRK) {

                throw new IOException(
                        "Unknown four-piece material code: "
                                + materialCode
                                + "."
                );
            }


            Color strongColor =
                    decodeColor(
                            data.readByte()
                    );


            int stateCount =
                    data.readInt();


            int expectedStateCount =
                    FourPieceTablebase.persistenceStateCount();


            if (stateCount
                    != expectedStateCount) {

                throw new IOException(
                        "Four-piece tablebase state count "
                                + stateCount
                                + " does not match expected "
                                + expectedStateCount
                                + "."
                );
            }


            byte[] outcome =
                    new byte[
                            stateCount
                            ];


            short[] distance =
                    new short[
                            stateCount
                            ];


            data.readFully(
                    outcome
            );


            for (int state = 0;
                 state < stateCount;
                 state++) {

                distance[state] =
                        data.readShort();
            }


            if (data.read()
                    != -1) {

                throw new IOException(
                        "Four-piece tablebase resource contains trailing data."
                );
            }


            FourPieceTablebase tablebase =
                    new FourPieceTablebase(
                            FourPieceMaterial.kqrk(
                                    strongColor
                            )
                    );


            tablebase.restoreFromPersistence(
                    outcome,
                    distance
            );


            return tablebase;

        } catch (EOFException exception) {

            throw new IOException(
                    "Four-piece tablebase resource is truncated.",
                    exception
            );
        }
    }


    private static void write(
            DataOutputStream output,
            FourPieceTablebase tablebase
    ) throws IOException {

        byte[] outcome =
                tablebase.copyOutcomeDataForPersistence();


        short[] distance =
                tablebase.copyDistanceDataForPersistence();


        output.writeInt(
                MAGIC
        );

        output.writeShort(
                VERSION
        );

        output.writeByte(
                MATERIAL_KQRK
        );

        output.writeByte(
                encodeColor(
                        tablebase.getMaterial()
                                .strongColor()
                )
        );

        output.writeInt(
                outcome.length
        );

        output.write(
                outcome
        );


        for (short value :
                distance) {

            output.writeShort(
                    value
            );
        }
    }


    private static byte encodeColor(
            Color color
    ) {

        if (color == Color.WHITE) {
            return COLOR_WHITE;
        }

        if (color == Color.BLACK) {
            return COLOR_BLACK;
        }

        throw new IllegalArgumentException(
                "Unsupported tablebase color: "
                        + color
        );
    }


    private static Color decodeColor(
            byte encoded
    ) throws IOException {

        return switch (encoded) {

            case COLOR_WHITE ->
                    Color.WHITE;

            case COLOR_BLACK ->
                    Color.BLACK;

            default ->
                    throw new IOException(
                            "Unknown four-piece color code: "
                                    + encoded
                                    + "."
                    );
        };
    }
}
