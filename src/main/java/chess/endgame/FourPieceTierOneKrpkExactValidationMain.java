package main.java.chess.endgame;

import main.java.chess.model.PieceType;

import java.util.Random;

/** Milestone 45: exact KRPK color-symmetry and Bellman validation. */
public final class FourPieceTierOneKrpkExactValidationMain {

    private static final int DEFAULT_BELLMAN_SAMPLES = 1_000_000;

    private FourPieceTierOneKrpkExactValidationMain() {}

    public static void main(String[] args) throws Exception {
        int samples = args.length == 0 ? DEFAULT_BELLMAN_SAMPLES : Integer.parseInt(args[0]);
        if (samples < 1) throw new IllegalArgumentException("Bellman sample count must be positive.");

        FourPieceMaterialClass material = FourPieceMaterialClass.sameSide(PieceType.ROOK, PieceType.PAWN);
        System.out.println("KRPK exact validation / color-symmetry gate");
        System.out.println("==========================================");
        System.out.println("\nBuilding strong-WHITE KRPK...");
        FourPieceTierOneRetrogradeBuilder.Result white = new FourPieceTierOneRetrogradeBuilder(material, true).build();
        System.out.println("\nBuilding strong-BLACK KRPK...");
        FourPieceTierOneRetrogradeBuilder.Result black = new FourPieceTierOneRetrogradeBuilder(material, false).build();

        validateMetadata(white, black);
        long t = System.nanoTime();
        SymmetryCounts symmetry = validateSymmetry(white, black);
        System.out.println("\nFull state-space color symmetry");
        System.out.println("===============================");
        System.out.println("States compared: " + symmetry.statesCompared());
        System.out.println("Legal states compared: " + symmetry.legalStatesCompared());
        System.out.println("Outcome mismatches: " + symmetry.outcomeMismatches());
        System.out.println("Distance mismatches: " + symmetry.distanceMismatches());
        System.out.printf("Elapsed: %.3f sec%n", (System.nanoTime()-t)/1_000_000_000.0);
        System.out.println("PASSED");

        BellmanCounts wb = validateBellman(white, material, true, samples, 0x4B52504B4501L);
        printBellman("WHITE", wb);
        BellmanCounts bb = validateBellman(black, material, false, samples, 0x4B52504B4502L);
        printBellman("BLACK", bb);

        String wd = FourPieceGenericTablebaseCodec.contentDigest(white.toTablebase());
        String bd = FourPieceGenericTablebaseCodec.contentDigest(black.toTablebase());
        System.out.println("\nSolved-tablebase metadata");
        System.out.println("=========================");
        System.out.println("Legal states: " + white.legalStates());
        System.out.println("WIN: " + white.wins());
        System.out.println("LOSS: " + white.losses());
        System.out.println("DRAW: " + white.draws());
        System.out.println("Maximum DTM: " + white.maximumDistance());
        System.out.println("WHITE digest: " + wd);
        System.out.println("BLACK digest: " + bd);
        System.out.println("\nKRPK EXACT VALIDATION / COLOR-SYMMETRY GATE PASSED");
        System.out.println("NEXT: PERSIST KRPK AND GENERALIZE TIER-1 RUNTIME ROUTING");
    }

    private static void validateMetadata(FourPieceTierOneRetrogradeBuilder.Result a, FourPieceTierOneRetrogradeBuilder.Result b) {
        require("material", a.material(), b.material());
        require("legal states", a.legalStates(), b.legalStates());
        require("WIN count", a.wins(), b.wins());
        require("LOSS count", a.losses(), b.losses());
        require("DRAW count", a.draws(), b.draws());
        require("maximum DTM", a.maximumDistance(), b.maximumDistance());
    }

    private static SymmetryCounts validateSymmetry(FourPieceTierOneRetrogradeBuilder.Result white, FourPieceTierOneRetrogradeBuilder.Result black) {
        long states=0, legal=0;
        for (int s=0; s<FourPieceGenericPrimitiveState.STATE_COUNT; s++) {
            int r=colorReverseState(s); states++;
            byte wo=white.outcome()[s], bo=black.outcome()[r];
            if (wo != FourPieceTablebase.INVALID) legal++;
            if (wo != bo) throw new IllegalStateException("KRPK color-symmetry outcome mismatch: "+describe(s)+" <-> "+describe(r));
            if (white.distance()[s] != black.distance()[r]) throw new IllegalStateException("KRPK color-symmetry DTM mismatch: "+describe(s)+" <-> "+describe(r));
        }
        return new SymmetryCounts(states,legal,0,0);
    }

    private static int colorReverseState(int state) {
        return FourPieceGenericPrimitiveState.encode(
                flip(FourPieceGenericPrimitiveState.blackKing(state)),
                flip(FourPieceGenericPrimitiveState.whiteKing(state)),
                flip(FourPieceGenericPrimitiveState.firstExtra(state)),
                flip(FourPieceGenericPrimitiveState.secondExtra(state)),
                !FourPieceGenericPrimitiveState.blackToMove(state));
    }

    private static int flip(int sq) { return (7-(sq>>>3))*8+(sq&7); }

    private static BellmanCounts validateBellman(FourPieceTierOneRetrogradeBuilder.Result result, FourPieceMaterialClass material, boolean ownerWhite, int samples, long seed) {
        Random random=new Random(seed);
        FourPieceTierOnePrimitiveMoveGenerator.Buffer moves=new FourPieceTierOnePrimitiveMoveGenerator.Buffer(64);
        int checked=0; long attempts=0, edges=0, wins=0, losses=0, draws=0, terminals=0;
        while (checked < samples) {
            attempts++; int state=randomState(random); byte o=result.outcome()[state];
            if (o==FourPieceTablebase.INVALID) continue;
            int n=FourPieceTierOnePrimitiveMoveGenerator.generateLegalSuccessors(state,material,ownerWhite,moves);
            if (n==0) {
                terminals++;
                if (o==FourPieceTablebase.LOSS && result.distance()[state]!=0) fail("terminal LOSS DTM != 0",state,o,result.distance()[state]);
                if (o!=FourPieceTablebase.LOSS && o!=FourPieceTablebase.DRAW) fail("terminal is neither LOSS nor DRAW",state,o,result.distance()[state]);
                checked++; continue;
            }
            int lc=0,wc=0,dc=0,minLoss=Integer.MAX_VALUE,maxWin=Integer.MIN_VALUE; boolean external=false;
            for(int i=0;i<n;i++) {
                if(moves.boundaryType(i)!=FourPieceTierOnePrimitiveMoveGenerator.BOUNDARY_NONE){external=true;break;}
                edges++; int c=moves.state(i); byte co=result.outcome()[c]; short cd=result.distance()[c];
                if(co==FourPieceTablebase.LOSS){lc++;minLoss=Math.min(minLoss,cd);}
                else if(co==FourPieceTablebase.WIN){wc++;maxWin=Math.max(maxWin,cd);}
                else if(co==FourPieceTablebase.DRAW)dc++;
                else fail("same-class child unresolved/invalid",c,co,cd);
            }
            if(external) continue;
            short d=result.distance()[state];
            if(o==FourPieceTablebase.WIN){wins++; if(lc==0)fail("WIN has no LOSS child",state,o,d); if(d!=minLoss+1)fail("WIN DTM recurrence mismatch",state,o,d);}
            else if(o==FourPieceTablebase.LOSS){losses++; if(wc!=n)fail("LOSS does not have all WIN children",state,o,d); if(d!=maxWin+1)fail("LOSS DTM recurrence mismatch",state,o,d);}
            else if(o==FourPieceTablebase.DRAW){draws++; if(lc!=0)fail("DRAW has LOSS child",state,o,d); if(dc==0)fail("DRAW has no DRAW child",state,o,d); if(d!=-1)fail("DRAW DTM != -1",state,o,d);}
            else fail("legal state unresolved/invalid",state,o,d);
            checked++;
        }
        return new BellmanCounts(checked,attempts,edges,wins,losses,draws,terminals);
    }

    private static int randomState(Random r){
        while(true){int wk=r.nextInt(64),bk=r.nextInt(64),rook=r.nextInt(64),pawn=(1+r.nextInt(6))*8+r.nextInt(8);
            if(wk==bk||wk==rook||wk==pawn||bk==rook||bk==pawn||rook==pawn)continue;
            return FourPieceGenericPrimitiveState.encode(wk,bk,rook,pawn,r.nextBoolean());}
    }

    private static void printBellman(String side,BellmanCounts c){
        System.out.println("\nBellman recurrence — strong "+side);
        System.out.println("=================================");
        System.out.println("Legal states checked: "+c.legalStatesChecked());
        System.out.println("Random attempts: "+c.randomAttempts());
        System.out.println("Same-class edges checked: "+c.sameClassEdgesChecked());
        System.out.println("WIN states checked: "+c.winsChecked());
        System.out.println("LOSS states checked: "+c.lossesChecked());
        System.out.println("DRAW states checked: "+c.drawsChecked());
        System.out.println("Terminal states checked: "+c.terminalStatesChecked());
        System.out.println("Bellman mismatches: 0"); System.out.println("PASSED");
    }

    private static void fail(String why,int s,byte o,short d){throw new IllegalStateException("KRPK Bellman validation failed: "+why+"\n  state: "+describe(s)+"\n  outcome: "+outcomeName(o)+"\n  DTM: "+d);}
    private static String describe(int s){return "WK="+alg(FourPieceGenericPrimitiveState.whiteKing(s))+" BK="+alg(FourPieceGenericPrimitiveState.blackKing(s))+" R="+alg(FourPieceGenericPrimitiveState.firstExtra(s))+" P="+alg(FourPieceGenericPrimitiveState.secondExtra(s))+" stm="+(FourPieceGenericPrimitiveState.blackToMove(s)?"BLACK":"WHITE")+" ["+s+"]";}
    private static String alg(int s){return ""+(char)('a'+(s&7))+((s>>>3)+1);}
    private static String outcomeName(byte o){if(o==FourPieceTablebase.WIN)return "WIN";if(o==FourPieceTablebase.LOSS)return "LOSS";if(o==FourPieceTablebase.DRAW)return "DRAW";if(o==FourPieceTablebase.INVALID)return "INVALID";return "UNKNOWN";}
    private static void require(String n,long a,long b){if(a!=b)throw new IllegalStateException("KRPK metadata mismatch for "+n+": "+a+" != "+b);}
    private static void require(String n,Object a,Object b){if(!a.equals(b))throw new IllegalStateException("KRPK metadata mismatch for "+n+": "+a+" != "+b);}
    private record SymmetryCounts(long statesCompared,long legalStatesCompared,long outcomeMismatches,long distanceMismatches){}
    private record BellmanCounts(int legalStatesChecked,long randomAttempts,long sameClassEdgesChecked,long winsChecked,long lossesChecked,long drawsChecked,long terminalStatesChecked){}
}
