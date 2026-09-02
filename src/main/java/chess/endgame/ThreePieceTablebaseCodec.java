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
 * Binary persistence codec for exact three-piece tablebases.
 *
 * Format v1:
 *
 *     int   magic      = "CTB3"
 *     short version    = 1
 *     byte  material   = 1 queen, 2 rook, 3 pawn
 *     byte  color      = 0 white, 1 black
 *     int   stateCount
 *     byte[stateCount] outcome
 *     short[stateCount] distance
 *
 * Shorts are written in DataOutputStream's normal big-endian order.
 */
public final class ThreePieceTablebaseCodec {

    private static final int MAGIC =
            0x43544233; // CTB3

    private static final short VERSION =
            1;

    private static final byte MATERIAL_QUEEN =
            1;

    private static final byte MATERIAL_ROOK =
            2;

    private static final byte MATERIAL_PAWN =
            3;

    private static final byte COLOR_WHITE =
            0;

    private static final byte COLOR_BLACK =
            1;


    private ThreePieceTablebaseCodec() {
    }


    public static void save(
            Path path,
            ThreePieceTablebase tablebase
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
                    "Tablebase must be built before it can be saved."
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


    public static ThreePieceTablebase load(
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


    public static ThreePieceTablebase load(
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
                        "Invalid three-piece tablebase magic."
                );
            }


            short version =
                    data.readShort();

            if (version != VERSION) {
                throw new IOException(
                        "Unsupported three-piece tablebase version: "
                                + version
                                + "."
                );
            }


            PieceType material =
                    decodeMaterial(
                            data.readByte()
                    );


            Color color =
                    decodeColor(
                            data.readByte()
                    );


            int stateCount =
                    data.readInt();


            int expectedStateCount =
                    ThreePieceTablebase.persistenceStateCount();


            if (stateCount != expectedStateCount) {

                throw new IOException(
                        "Tablebase state count "
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


            /*
             * A valid resource ends exactly here.  Trailing bytes usually
             * indicate a mismatched format or accidentally concatenated file.
             */
            if (data.read()
                    != -1) {

                throw new IOException(
                        "Tablebase resource contains trailing data."
                );
            }


            ThreePieceTablebase tablebase =
                    new ThreePieceTablebase(
                            material,
                            color
                    );


            tablebase.restoreFromPersistence(
                    outcome,
                    distance
            );


            return tablebase;

        } catch (EOFException exception) {

            throw new IOException(
                    "Tablebase resource is truncated.",
                    exception
            );
        }
    }


    private static void write(
            DataOutputStream output,
            ThreePieceTablebase tablebase
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
                encodeMaterial(
                        tablebase.getMajorType()
                )
        );

        output.writeByte(
                encodeColor(
                        tablebase.getMajorColor()
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


    private static byte encodeMaterial(
            PieceType material
    ) {

        return switch (material) {

            case QUEEN ->
                    MATERIAL_QUEEN;

            case ROOK ->
                    MATERIAL_ROOK;

            case PAWN ->
                    MATERIAL_PAWN;

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported three-piece material: "
                                    + material
                    );
        };
    }


    private static PieceType decodeMaterial(
            byte encoded
    ) throws IOException {

        return switch (encoded) {

            case MATERIAL_QUEEN ->
                    PieceType.QUEEN;

            case MATERIAL_ROOK ->
                    PieceType.ROOK;

            case MATERIAL_PAWN ->
                    PieceType.PAWN;

            default ->
                    throw new IOException(
                            "Unknown three-piece material code: "
                                    + encoded
                                    + "."
                    );
        };
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
                            "Unknown three-piece color code: "
                                    + encoded
                                    + "."
                    );
        };
    }
}
