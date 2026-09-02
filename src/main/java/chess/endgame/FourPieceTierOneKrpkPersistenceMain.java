package main.java.chess.endgame;

import main.java.chess.model.Board;
import main.java.chess.model.Color;
import main.java.chess.model.Piece;
import main.java.chess.model.PieceType;
import main.java.chess.model.Position;
import main.java.chess.model.PositionKey;
import main.java.chess.model.Square;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Milestone 46: persist validated KRPK and verify generic Tier-1 runtime service. */
public final class FourPieceTierOneKrpkPersistenceMain {
    private static final String EXPECTED_DIGEST =
            "a12d68b1e137bc0b462f03334e5b517c6fcbdda3d696128788f87be03ce60b1b";

    private FourPieceTierOneKrpkPersistenceMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length>1) throw new IllegalArgumentException(
                "Usage: FourPieceTierOneKrpkPersistenceMain [tablebase-directory]");

        Path directory = args.length==0 ? FourPieceTierOneTablebaseService.DEFAULT_DIRECTORY
                : Path.of(args[0]);
        directory=directory.toAbsolutePath().normalize();
        FourPieceMaterialClass material=FourPieceMaterialClass.sameSide(PieceType.ROOK,PieceType.PAWN);
        FourPieceTierOneTablebaseService service=new FourPieceTierOneTablebaseService(directory);
        Path path=service.assetPath(material);

        System.out.println("KRPK Tier-1 persistence / generic-runtime gate");
        System.out.println("============================================");
        System.out.println("Output: "+path);
        System.out.println("\nBuilding canonical strong-WHITE KRPK...");

        FourPieceTierOneRetrogradeBuilder.Result result=
                new FourPieceTierOneRetrogradeBuilder(material,true).build();
        FourPieceGenericTablebase solved=result.toTablebase();
        String solvedDigest=FourPieceGenericTablebaseCodec.contentDigest(solved);
        System.out.println("Solved digest: "+solvedDigest);
        if(!EXPECTED_DIGEST.equals(solvedDigest)) throw new IllegalStateException(
                "KRPK solve digest changed. Expected "+EXPECTED_DIGEST+", found "+solvedDigest);

        System.out.println("\nSaving...");
        long t=System.nanoTime();
        FourPieceGenericTablebaseCodec.save(solved,path);
        System.out.printf("Saved in %.3f sec%n",(System.nanoTime()-t)/1e9);
        System.out.printf("Compressed size: %.2f MiB%n",Files.size(path)/(1024.0*1024.0));

        System.out.println("\nReloading..."); t=System.nanoTime();
        FourPieceGenericTablebase loaded=FourPieceGenericTablebaseCodec.load(path);
        System.out.printf("Loaded in %.3f sec%n",(System.nanoTime()-t)/1e9);
        verifyMetadata(solved,loaded);
        String loadedDigest=FourPieceGenericTablebaseCodec.contentDigest(loaded);
        System.out.println("Loaded digest: "+loadedDigest);
        if(!solvedDigest.equals(loadedDigest)) throw new IllegalStateException("KRPK persistence digest mismatch.");
        System.out.println("Byte-for-byte persistence identity: PASSED");

        int fixture=findDecisiveFixture(loaded);
        Position white=positionFromCanonicalState(fixture,PieceType.ROOK);
        Position black=colorReversePosition(fixture,PieceType.ROOK);
        Optional<FourPieceTierOneTablebaseService.ProbeResult> wp=service.probe(white);
        Optional<FourPieceTierOneTablebaseService.ProbeResult> bp=service.probe(black);
        if(wp.isEmpty()||bp.isEmpty()) throw new IllegalStateException("Generic Tier-1 service rejected KRPK fixture.");
        var w=wp.get(); var b=bp.get();
        if(w.primitiveState()!=fixture||b.primitiveState()!=fixture)
            throw new IllegalStateException("KRPK generic runtime normalization changed primitive state.");
        if(w.outcome()!=b.outcome()||w.distance()!=b.distance())
            throw new IllegalStateException("KRPK generic runtime normalization changed WDL/DTM.");
        if(w.colorReversed()||!b.colorReversed())
            throw new IllegalStateException("KRPK generic runtime color-reversal flags are incorrect.");

        System.out.println("\nRuntime normalization fixture");
        System.out.println("=============================");
        System.out.println("Canonical state: "+fixture);
        System.out.println("Outcome: "+w.outcomeName());
        System.out.println("DTM: "+w.distance());
        System.out.println("Strong-WHITE primitive state: "+w.primitiveState());
        System.out.println("Strong-BLACK normalized state: "+b.primitiveState());
        System.out.println("Color normalization: PASSED");

        // Existing KQPK asset must also be recognized by the new generic service.
        FourPieceMaterialClass kqpk=FourPieceMaterialClass.sameSide(PieceType.QUEEN,PieceType.PAWN);
        Path kqpkPath=service.assetPath(kqpk);
        if(Files.isRegularFile(kqpkPath)) {
            FourPieceGenericTablebase q=FourPieceGenericTablebaseCodec.load(kqpkPath);
            int qFixture=findDecisiveFixture(q);
            if(service.probe(positionFromCanonicalState(qFixture,PieceType.QUEEN)).isEmpty())
                throw new IllegalStateException("Generic Tier-1 service failed existing KQPK asset.");
            System.out.println("Existing KQPK generic-service regression: PASSED");
        } else {
            throw new IllegalStateException("Existing KQPK asset is missing: "+kqpkPath);
        }

        service.clearCache();
        if(service.loadedCount()!=0) throw new IllegalStateException("Generic Tier-1 cache did not clear.");
        System.out.println("Runtime cache clear: PASSED");
        System.out.println("\nKRPK PERSISTENCE / GENERIC TIER-1 RUNTIME GATE PASSED");
        System.out.println("NEXT: VERIFY KQPK + KRPK THROUGH EXACT FACADE / CONTROLLER");
    }

    private static void verifyMetadata(FourPieceGenericTablebase a,FourPieceGenericTablebase b){
        if(!a.material().equals(b.material())||a.sameSideOwnerIsWhite()!=b.sameSideOwnerIsWhite()
                ||a.legalStates()!=b.legalStates()||a.wins()!=b.wins()||a.losses()!=b.losses()
                ||a.draws()!=b.draws()||a.maximumDistance()!=b.maximumDistance())
            throw new IllegalStateException("Reloaded KRPK metadata mismatch.");
    }
    private static int findDecisiveFixture(FourPieceGenericTablebase t){
        for(int s=0;s<FourPieceGenericPrimitiveState.STATE_COUNT;s++){
            byte o=t.outcome(s); if((o==FourPieceTablebase.WIN||o==FourPieceTablebase.LOSS)&&t.distance(s)>=4)return s;
        } throw new IllegalStateException("Unable to find decisive Tier-1 fixture.");
    }
    private static Position positionFromCanonicalState(int s,PieceType type){
        Board b=new Board();
        b.setPiece(square(FourPieceGenericPrimitiveState.whiteKing(s)),new Piece(PieceType.KING,Color.WHITE));
        b.setPiece(square(FourPieceGenericPrimitiveState.blackKing(s)),new Piece(PieceType.KING,Color.BLACK));
        b.setPiece(square(FourPieceGenericPrimitiveState.firstExtra(s)),new Piece(type,Color.WHITE));
        b.setPiece(square(FourPieceGenericPrimitiveState.secondExtra(s)),new Piece(PieceType.PAWN,Color.WHITE));
        return position(b,FourPieceGenericPrimitiveState.blackToMove(s)?Color.BLACK:Color.WHITE);
    }
    private static Position colorReversePosition(int s,PieceType type){
        Board b=new Board();
        b.setPiece(square(flip(FourPieceGenericPrimitiveState.blackKing(s))),new Piece(PieceType.KING,Color.WHITE));
        b.setPiece(square(flip(FourPieceGenericPrimitiveState.whiteKing(s))),new Piece(PieceType.KING,Color.BLACK));
        b.setPiece(square(flip(FourPieceGenericPrimitiveState.firstExtra(s))),new Piece(type,Color.BLACK));
        b.setPiece(square(flip(FourPieceGenericPrimitiveState.secondExtra(s))),new Piece(PieceType.PAWN,Color.BLACK));
        Color stm=FourPieceGenericPrimitiveState.blackToMove(s)?Color.BLACK:Color.WHITE;
        return position(b,stm.opposite());
    }
    private static Position position(Board b,Color stm){
        Position temp=new Position(b,stm,false,false,false,false,null,0,1,new HashMap<>());
        Map<PositionKey,Integer> reps=new HashMap<>(); reps.put(temp.createPositionKey(),1);
        return new Position(b,stm,false,false,false,false,null,0,1,reps);
    }
    private static Square square(int s){return new Square(s&7,s>>>3);}
    private static int flip(int s){return (7-(s>>>3))*8+(s&7);}
}
