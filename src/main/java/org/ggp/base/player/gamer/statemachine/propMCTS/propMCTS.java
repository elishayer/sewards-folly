package org.ggp.base.player.gamer.statemachine.propMCTS;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;

public class propMCTS extends StateMachineGamer
{
	/* Node Structure for state tree
	 */
	public class Node {
		private List<Node> children = null;
		private double score;
		private MachineState state;
		private int visits;
		private Node parent;
		private List<Move> moves;

		public Node(MachineState state, double score, Node parent, List<Move> moves) {
			this.children = new ArrayList<>();
			this.score = score;
			this.state = state;
			this.visits = 0;
			this.parent = parent;
			this.moves = moves;
		}
	}

	static long DEPTH_TIME = 1500;
	static long SEARCH_TIME = 2000;
	static int LEVEL = 2;
	static Random r = new Random();

	private int subgameIndex;
	private double expansionFactor;
	private int expansionFactorTotal = 0;
	private int expansionFactorNum = 0;
	private double explorationTime;
	private int numCharges;

	private int chargesSent;

	private Node curNode;

	private long endtime;

	boolean first = true;

	@Override
	public String getName() {
		return "SewardsFollyPropnetMCTS";
	}

	@Override
    public StateMachine getInitialStateMachine() {
        return new CachedStateMachine(new SamplePropNetStateMachine());
    }

	@Override
    public DetailPanel getDetailPanel() {
        return new SimpleDetailPanel();
    }

	@Override
    public void stateMachineAbort() {
        // Propnet MCTS gamer has no special cleanup when the match ends abruptly.
    }

    @Override
    public void stateMachineStop() {
        // Propnet MCTS gamer has no special cleanup when the match ends normally.
    }

    @Override
    public void preview(Game g, long timeout) throws GamePreviewException {
        // Propnet MCTS gamer has no game previewing.
    }

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		endtime = timeout;
		StateMachine machine = getStateMachine();
		int numSubgames = machine.getNumSubgames();

		long depth_start = System.currentTimeMillis();

		long subgameMetaTime = (timeout - System.currentTimeMillis()) / 2;
		long subgameMetaTimePerGame = subgameMetaTime / numSubgames;
		System.out.println("subgameMetaTimePerGame: " + subgameMetaTimePerGame);

		List<Integer> chargeList = new ArrayList<Integer>();
		List<Double> scoreList = new ArrayList<Double>();
		for (int i = 0; i < numSubgames; i++) {
			System.out.println("game: " + i + " of " + numSubgames);
			chargeList.add(0);
			scoreList.add(0.0);
			long subgameEnd = System.currentTimeMillis() + subgameMetaTimePerGame;
			while(System.currentTimeMillis() < subgameEnd) {
				//System.out.println("test");
				double score = depthCharge(machine, machine.getRoles(), getRole(), machine.getInitialState(), true, i, 0);
				scoreList.set(i, scoreList.get(i) + score);
				chargeList.set(i, chargeList.get(i) + 1);
			}
			scoreList.set(i, scoreList.get(i) / chargeList.get(i));
			System.out.println("total number of charges: " + chargeList.get(i));
			System.out.println("total score: " + scoreList.get(i));
			long expTime = 1 + subgameMetaTimePerGame / chargeList.get(i);
			System.out.println("observed time per depth charge: " + expTime);
			double expFactor = expansionFactorTotal / (double) expansionFactorNum;
			System.out.println("expansion factor: " + expFactor);
			expansionFactorTotal = 0;
			expansionFactorNum = 0;

			int nCharges = (int) (subgameMetaTimePerGame / (expTime * Math.pow(expFactor, LEVEL)));
			chargeList.set(i, nCharges);
			System.out.println("score: " + scoreList.get(i) + " | numCharges: " + chargeList.get(i));
		}
		numCharges = chargeList.get(0);
		subgameIndex = 0;
		for (int i = 0; i < numSubgames; i++) {
			if (scoreList.get(i) < scoreList.get(subgameIndex)) {
				numCharges = chargeList.get(i);
				subgameIndex = i;
			}
		}
		System.out.println("Num charges: " + numCharges + " | Subgame index: " + subgameIndex);

		curNode = new Node(machine.getInitialState(), 0, null, null);

		int nodesExplored = 0;
		chargesSent = 0;
		depth_start = System.currentTimeMillis();
		while(timeout - System.currentTimeMillis() >= SEARCH_TIME) {
			Node selected = select(curNode);
			expand(selected, machine, getRole());
			nodesExplored++;
		}
		System.out.println("nodes explored: " + nodesExplored);
		System.out.println(chargesSent);
		System.out.println("charges/second: " + (chargesSent / ((System.currentTimeMillis() - depth_start) / 1000.0 )));

		first = true;
	}

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		endtime = timeout;
    	chargesSent = 0;

    	System.out.println("NEW SELECT");

		numCharges = (int) ((timeout - System.currentTimeMillis()) / (explorationTime * Math.pow(expansionFactor, LEVEL)));
		if(numCharges == 0) {
			numCharges = 1;
		}
		//numCharges = 100;
		System.out.println("num charges: " + numCharges);

    	long start = System.currentTimeMillis();
		StateMachine machine = getStateMachine();

		List<Move> moves = machine.findLegals(getRole(), getCurrentState(), subgameIndex);

		Node curState = null;
		//System.out.println("is first: " + first);
		if(first) {
			curState = curNode;
			first = false;
			System.out.println("first");
			System.out.println(curState);
		} else {
			MachineState state = getCurrentState();
			for(int i = 0; i < curNode.children.size(); i++) {
				if(curNode.children.get(i).state.equals(state)) {
					curState = curNode.children.get(i);
					curNode = curState;
					//System.out.println("chosen");
					break;
				}
			}
		}

		//selects and expands on a node until time is up or all nodes have been searched
		while(timeout - System.currentTimeMillis() >= SEARCH_TIME) {
			Node selected = select(curState);
			expand(selected, machine, getRole());
		}

		System.out.println("charges sent: " + chargesSent);

		//System.out.println(getNodeCount(curState, machine));
		Role role = getRole();
		List<Role> roles = machine.getRoles();


		int roleIndex = 0;
		for(int i = 0; i < roles.size(); i++) {
			if(role.equals(roles.get(i))) {
				roleIndex = i;
				break;
			}
		}

		//selects the best move based on the scores of the child nodes
		Move bestMove = null;
		double bestScore = 0;
		for (int i = 0; i < curState.children.size(); i++) {
			System.out.println("move: " + curState.children.get(i).moves.get(roleIndex)+ " | raw score: " + curState.children.get(i).score + " | visits: " + curState.children.get(i).visits + " | true score " + selectfn(curState.children.get(i)));
			if (curState.children.get(i).score >= bestScore) {
				bestMove = curState.children.get(i).moves.get(roleIndex);
				bestScore = curState.children.get(i).score;
			}
		}

		long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
        return bestMove;
    }

    /* selects a node to expand
     */
    private Node select(Node node) {
    	//don't look further
    	if (node == null || node.visits == 0)	{
    		return node;
    	}

    	//choose unvisited child
    	for (int i = 0; i < node.children.size(); i++) {
    		//System.out.println(node.children.get(i).visits);
    		if (node.children.get(i).visits == 0 ) {
    			return node.children.get(i);
    		}
    	}

    	//choose from visited childs based on scores and visits


    	double score = 0;
    	Node result = null;
    	for (int i = 0; i < node.children.size(); i++) {
    		double newscore = selectfn(node.children.get(i));
    		if (newscore > score) {
    			score = newscore;
    			result = node.children.get(i);
    		}
    	}

    	return select(result);
    }

    double selectfn(Node node) {
    	return (double) (node.score + 50*Math.sqrt(Math.log(node.parent.visits)/node.visits));
    }

    /*
     * expands on a node and gets scores for the node and its new children
     */
    private void expand(Node node, StateMachine machine, Role role) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	//avoid expanding a null node
    	if (node == null) {
    		//System.out.println("null");
    		return;
    	}

		//if terminal then a MCS is not needed
    	if (machine.findTerminalp(node.state, subgameIndex, 0)) {
    		//System.out.println("term");
    		node.visits++;
    		int score = machine.findReward(role, node.state, subgameIndex, 0);
    		node.score = score;
    		backpropogate(node.parent, score);
    		return;
    	}

    	//run a MCS for each action
    	System.out.println(node.state);
    	List<List<Move>> actions = getActions(machine.getRoles(), machine, node.state);
    	List<Move> action = actions.get(r.nextInt(actions.size()));

    	MachineState newstate = machine.getNextState(node.state, action);
    	double totalscore = 0;
    	for(int j = 0; j < numCharges; j++) { //place holder #
    		if((endtime - System.currentTimeMillis() < SEARCH_TIME) || j > 10000) break;
    		double score = (double) depthCharge(machine, machine.getRoles(), role, newstate, false, subgameIndex, 0);
    		totalscore += score;
    		chargesSent++;

    	}
		//System.out.println(chargesSent);

    	totalscore /= numCharges;
   		//add nodes to tree
   		Node newNode = new Node(newstate, totalscore, node, action);
   		node.children.add(newNode);
   		backpropogate(newNode.parent, totalscore);

   		for(int i = 0; i < actions.size(); i++) {
   			if(!actions.get(i).equals(action)) {
   		    	newstate = machine.getNextState(node.state, actions.get(i));

   				newNode = new Node(newstate, 0, node, actions.get(i));
   				node.children.add(newNode);
   			}
   		}

    }

    private void backpropogate(Node node, double score) {
    	node.visits++;
    	node.score += (score - node.score)/node.visits;
    	//adjust to avoid always looking at the same nodes
    	if(node.parent != null) {
    		backpropogate(node.parent, score);
    	}
    }

    private double depthCharge(StateMachine machine, List<Role> roles, Role role, MachineState state, boolean meta, int gameIndex, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	while(!machine.findTerminalp(state, gameIndex, level)) {
    		List<Move> actions = new ArrayList<Move>();
        	for (int i = 0; i < roles.size(); i++) {
        		List<Move> legals = machine.findLegals(roles.get(i), state, gameIndex);
        		//System.out.println("legal for role" + i + " : " + legals);
        		if (meta && roles.get(i).equals(role)) {
        			expansionFactorTotal += legals.size();
        			expansionFactorNum++;
        		}
        		if (legals.size() > 0) {
        			actions.add(legals.get(r.nextInt(legals.size())));
        		} else {
        			actions.add(null);
        		}
        	}
        	state = machine.getNextState(state, actions);
    	}

    	return machine.findReward(role, state, gameIndex, level);
    }

    // get a list of all possible permutations of actions
    // by all players (possible more than 2)
    private List<List<Move> > getActions(List<Role> roles,
    		StateMachine machine, MachineState state) throws MoveDefinitionException {
    	List<List<Move> > actions = new ArrayList<List<Move> >();
    	List<Move> curr = new ArrayList<Move>();
    	recursiveActionHelper(roles, machine, state, 0, curr, actions);
    	return actions;
    }

    // recursive action possibility builder
    private void recursiveActionHelper(List<Role> roles,
    		StateMachine machine, MachineState state, int i,
    		List<Move> curr, List<List<Move> > actions) throws MoveDefinitionException {
    	if (i == roles.size()) {
    		actions.add(curr);
    		return;
    	} else {
    		List<Move> possibleActions;
    		if(roles.get(i) == getRole()) {
    			possibleActions = machine.getLegalMoves(state, roles.get(i), subgameIndex);
    		} else {
    			possibleActions = machine.getOthersLegalMoves(state, roles.get(i));
    		}
    		for (int j = 0; j < possibleActions.size(); j++) {
    			List<Move> dest = new ArrayList<Move>(curr);
    			dest.add(possibleActions.get(j));
    			recursiveActionHelper(roles, machine, state, i + 1, dest, actions);
    		}
    	}
    }
}