package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;


@SuppressWarnings("unused")
public class SamplePropNetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    private boolean factor = true;

    private List<Proposition> latches = new ArrayList<Proposition>();
    private HashMap<Proposition, Integer> deadStates = new HashMap<Proposition, Integer>();

    /** The relevant legal moves by subgame, post factoring */
    List<Set<Component> > subgameLegals = new ArrayList<Set<Component> >();

    private long setTime = 0;

    int smallest, secondSmallest, smallestIndex;

    @Override
    public long setTime() {
    	return setTime;
    }

    @Override
    public int getNumSubgames() {
    	if (roles.size() == 1 && factor) {
    		System.out.println("Num subgames requested, num is: " + subgameLegals.size());
        	return subgameLegals.size();
    	} else {
    		return 1;
    	}

    }

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     * @throws TransitionDefinitionException
     */
    @Override
    public void initialize(List<Gdl> description) throws TransitionDefinitionException {
        long start = System.currentTimeMillis();

    	try {
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            ordering = getOrdering();

            // removeAnons();

            // factoring for single player only
            if (roles.size() == 1 && factor) {
	            // factor the propnet, removing any unneeded propsitions
	            factorAnalysis();

	            for (int i = 0; i < subgameLegals.size(); i++) {
	            	System.out.println(subgameLegals.get(i).size());
	            }
            }

            identifyLatches();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    	long end = System.currentTimeMillis();
    	System.out.println("propnet size: " + propNet.getPropositions().size());
    	System.out.println("time to build propnet: " + (end - start));
    	propNet.renderToFile("propnet-viz.dot");
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state, int gameIndex, int level) {
    	System.out.println(state);
    	if (roles.size() == 1 && factor) {
        	int length = gameIndex == smallestIndex ? secondSmallest : smallest;
        	if (level >= length) return true;
    	}
    	setPropnet(state, null);
    	for (Proposition p: deadStates.keySet()) {
    		if (p.getValue()) {
    			System.out.println("deadstate reached");
    			return true;
    		}
    	}

    	return propNet.getTerminalProposition().getValue();
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role, int gameIndex, int level)
            throws GoalDefinitionException {
    	if (roles.size() == 1 && factor) {
        	int length = gameIndex == smallestIndex ? secondSmallest : smallest;
        	if (level >= length) return 0;
    	}
        setPropnet(state, null);
    	for(Proposition p: deadStates.keySet()) {
    		if(p.getValue()) {
    			System.out.println("exiting with value " + deadStates.get(p));
    			return deadStates.get(p);
    		}
    	}

        List<Role> roles = propNet.getRoles();
        Set<Proposition> rewards = new HashSet<Proposition>();
        for (int i = 0; i < roles.size(); i++) {
        	if (roles.get(i).equals(role)) {
        		rewards = propNet.getGoalPropositions().get(role);
        		break;
        	}
        }
        Proposition reward = null;
        for (Proposition p : rewards) {
        	if (p.getValue()) {
        		if (reward != null) throw new GoalDefinitionException(state, role);
        		reward = p;
        	}
        }
        if (reward == null) return 0; //throw new GoalDefinitionException(state, role);
        return getGoalValue(reward);
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
    	setPropnet(null, null);
    	propNet.getInitProposition().setValue(true);
    	return getStateFromBase();
    }

    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
    	Set<Proposition> legals = new HashSet<Proposition>();
    	for (int i = 0; i < roles.size(); i++) {
    		if (roles.get(i).equals(role)) {
    			legals = propNet.getLegalPropositions().get(role);
    		}
    	}
    	List<Move> actions = new ArrayList<Move>();
    	for (Proposition p : legals) {
    		actions.add(getMoveFromProposition(p));
    	}
        return actions;
    }

    /**
     * Computes the legal moves for role in state.
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role, int gameIndex)
            throws MoveDefinitionException {
    	setPropnet(state, null);
    	List<Role> roles = propNet.getRoles();
    	Set<Proposition> legals = new HashSet<Proposition>();
    	for (int i = 0; i < roles.size(); i++) {
    		if (roles.get(i).equals(role)) {
    			legals = propNet.getLegalPropositions().get(role);
    		}
    	}
    	//System.out.println("legals " + legals);
    	List<Move> actions = new ArrayList<Move>();
    	for (Proposition p : legals) {
    		if (roles.size() == 1 && factor) {
        		if ((subgameLegals.get(gameIndex).contains(p) || propNet.getLegalInputMap().get(p).getOutputs().size() == 0) && p.getValue()) {
        			actions.add(getMoveFromProposition(p));
        		}
    		} else {
        		if (p.getValue()) {
        			actions.add(getMoveFromProposition(p));
        		}
    		}
    	}
        return actions;
    }


    /**
     * Computes the legal moves for other players in state.
     */
    @Override
    public List<Move> getOthersLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
    	setPropnet(state, null);
    	List<Role> roles = propNet.getRoles();
    	Set<Proposition> legals = new HashSet<Proposition>();
    	for (int i = 0; i < roles.size(); i++) {
    		if (roles.get(i).equals(role)) {
    			legals = propNet.getLegalPropositions().get(role);
    		}
    	}
    	//System.out.println("legals " + legals);
    	List<Move> actions = new ArrayList<Move>();
    	for (Proposition p : legals) {
    		if (p.getValue()) {
    			actions.add(getMoveFromProposition(p));
    		}
    	}
        return actions;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
    	setPropnet(state, moves);
        return getStateFromBase();
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        List<Proposition> order = new LinkedList<Proposition>();

        // List to contain the topological ordering of all propositions (base, input, view)
        // and all components
        List<Component> fullOrder = new ArrayList<Component>();

        // All of the components in the PropNet
        List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // Compute the topological ordering on all components
        while (true) {
        	if (components.isEmpty()) break;
        	for (int i = 0; i < components.size(); i++) {
        		if (canAdd(fullOrder, components.get(i))) {
        			fullOrder.add(components.get(i));
        			components.remove(components.get(i));
        			i--;
        		}
        	}
        }

        for (int i = 0; i < fullOrder.size(); i++) {
        	// include propositions, except base, input, and init propositions
        	// which is to say, only view propositions
        	if (propNet.getPropositions().contains(fullOrder.get(i)) &&
        			!propNet.getBasePropositions().containsValue(fullOrder.get(i)) &&
        			!propNet.getInputPropositions().containsValue(fullOrder.get(i)) &&
        			!propNet.getInitProposition().equals(fullOrder.get(i))) {
        		order.add((Proposition) fullOrder.get(i));
        	}
        }

        /*
        System.out.println("bases");
        for(Proposition p: propNet.getBasePropositions().values()) {
        	System.out.println(p);

        }
        System.out.println("");

        for(Proposition p: order) {
        	System.out.println(p);

        }
        int i = 1/0;
        */
        return order;
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    private void removeAnons() {
    	List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());
    	for(int i = 0; i < propositions.size(); i++) {
    		System.out.println("prop " + i + " of " + propositions.size());
    		Proposition p = propositions.get(i);
    		System.out.println(p);
    		if(p.getName().toString() == "anon") {
    			System.out.println("removed");
    			if(p.getOutputs().size() != 0 && p.getInputs().size() != 0) {
    				p.getSingleOutput().addInput(p.getSingleInput());
    				p.getSingleInput().addOutput(p.getSingleOutput());
    			}
    			propNet.removeComponent(p);
    			propositions.remove(i);
    			i--;
    		}
       	}
    }


    /**
     * Predicate function for whether the component c should be added
     * to fullOrder, an ordered list of components from the bases and inputs
     * to the end of the ordered list of components. The rules for adding are:
     * 1) any base proposition can be added
     * 2) any input proposition can be added
     * 3) other components can be added if all of their parents were previously added
     */
    private boolean canAdd(List<Component> fullOrder, Component c) {
    	if (propNet.getBasePropositions().containsValue(c)) return true;
    	if (propNet.getInputPropositions().containsValue(c)) return true;
    	for (Component input : c.getInputs()) {
    		if (!fullOrder.contains(input)) {
    			return false;
    		}
    	}
    	return true;
    }


    /* Helper methods */

    /**
     * Mark the base propositions given the current state of the game.
     * Gets all the GDL sentences corresponding to the passed in state,
     * and then marks the base propositions' value according to whether they
     * are present in the state's GDL sentences
     */
    private void markBases(MachineState state) {
    	Set<GdlSentence> sentences = state.getContents();

    	HashMap<GdlSentence, Proposition> basesMap = (HashMap<GdlSentence, Proposition>) propNet.getBasePropositions();
    	Set<GdlSentence> bases = basesMap.keySet();

    	for (GdlSentence base : bases) {
    		basesMap.get(base).setValue(sentences.contains(base));
    	}
    }

    /**
     * Mark the input propositions given the list of moves of all players.
     * Gets all the GDL sentences corresponding to the passed in moves,
     * and then marks the input propositions' value according to whether they
     * are present in the doeses.
     */

    private void markActions(List<Move> moves) {
    	List<GdlSentence> doeses = toDoes(moves);

    	HashMap<GdlSentence, Proposition> inputMap = (HashMap<GdlSentence, Proposition>) propNet.getInputPropositions();
    	Set<GdlSentence> inputs = inputMap.keySet();

    	for (GdlSentence input : inputs) {
    		inputMap.get(input).setValue(doeses.contains(input));
    	}
    }

    /**
     * Mark all propositions in the propnet as false
     */
    private void clearPropnet() {
    	for (Proposition p : propNet.getPropositions()) {
    		p.setValue(false);
    	}
    }

    /**
     * Set the propnet by clearing all propositions, marking the basis
     * if state is non-null, marking the actions if moves is non-null,
     * and setting the value of each of the view propositions in order
     */
    private void setPropnet(MachineState state, List<Move> moves) {
    	long start = System.currentTimeMillis();

    	clearPropnet();
    	if (state != null) markBases(state);
    	if (moves != null) markActions(moves);
    	for (Proposition p : ordering) {
    		p.setValue(p.getSingleInput().getValue());
    	}

    	setTime += System.currentTimeMillis() - start;
    }

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        //System.out.println(moves);
    	List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            if(moves.get(index) != null) {
            	doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
            }
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }

    private void factorAnalysis() throws TransitionDefinitionException {
    	System.out.println("factorAnalysis called");
    	Proposition terminal = propNet.getTerminalProposition();

    	List<Component> subgameTerminals = new ArrayList<Component>();
    	splitSubgames(terminal.getSingleInput(), subgameTerminals);

    	smallest = secondSmallest = Integer.MAX_VALUE;
    	if (subgameTerminals.size() <= 2) {
    		factor = false;
    		return;
    	}

    	List<Integer> lengthArray = new ArrayList<Integer>();
    	for (Component subgameTerminal : subgameTerminals) {
    		lengthArray.add(getSubgameLength(subgameTerminal));
    	}
    	//System.out.println("Length array: " + lengthArray);

    	smallestIndex = -1;
    	for (int i = 0; i < lengthArray.size(); i++) {
    		if (lengthArray.get(i) < smallest || smallestIndex < 0) {
    			secondSmallest = smallest;

    			smallest = lengthArray.get(i);
    			smallestIndex = i;
    		} else if (lengthArray.get(i) < secondSmallest) {
    			secondSmallest = lengthArray.get(i);
    		}
    	}
    	System.out.println("num games: " + subgameTerminals.size());
    	for (Component subgameTerminal : subgameTerminals) {
    		System.out.println(subgameTerminal);
    		Set<Component> subgameRelevant = new HashSet<Component>();
    		Set<Component> legals = new HashSet<Component>();
    		backpropogateFactoring(subgameTerminal, subgameRelevant, legals);
    		subgameLegals.add(legals);
    	}


    	//System.out.println("total num propositions: " + propNet.getComponents().size());
    	//System.out.println("factored");
    }

    private void splitSubgames(Component terminal, List<Component> subgameTerminals) {
    	System.out.println(terminal);
    	if (terminal instanceof Or) {
    		for (Component prev : terminal.getInputs()) {
    			splitSubgames(prev, subgameTerminals);
    		}
    	} else {
    		subgameTerminals.add(terminal);
    	}
    }

    private int getSubgameLength(Component terminal) throws TransitionDefinitionException {
    	for (Proposition p : propNet.getPropositions()) {
    		p.setValue(false);
    	}
    	propNet.getInitProposition().setValue(true);
    	Set<MachineState> prevStates = new HashSet<MachineState>();
    	int count = 1;

    	MachineState state = getStateFromBase();

    	while (!prevStates.contains(state = getNextState(state, null))) {
    		if (terminal.getValue()) return count;
    		prevStates.add(state);
    		count++;
    	}
    	return Integer.MAX_VALUE;
    }

    private void backpropogateFactoring(Component component, Set<Component> relevant, Set<Component> legals) {
    	if (relevant.contains(component)) return;
    	if (propNet.getInputPropositions().containsValue(component)) {
    		//System.out.println("legal found");
    		Proposition legal = propNet.getLegalInputMap().get(component);
    		legals.add(legal);
    	}
    	relevant.add(component);
    	for (Component input : component.getInputs()) {
    		backpropogateFactoring(input, relevant, legals);
    	}
    }

    // put all latches into the global latches array
    private void identifyLatches() {
    	Map<GdlSentence, Proposition> baseMap = propNet.getBasePropositions();
    	for (GdlSentence key : baseMap.keySet()) {
    		Proposition base = baseMap.get(key);
    		base.setValue(true);

    		Set<Component> visited = new HashSet<Component>();

    		if (isLatch(base, true, base, visited, true)) {
    			System.out.println("is a latch:  " + base);
    			latches.add(base);
    		} else {
    			System.out.println("NOT a latch: " + base);
    		}
    	}
    }

    private boolean isLatch(Component original, boolean target, Component c, Set<Component> visited, boolean first) {
    	if (c instanceof Transition) {
    		Component output = c.getSingleOutput();
    		return output.equals(original) && output.getValue() == target;
    	}
    	if (!first && original.equals(c)) return c.getValue() == target;
    	if (visited.contains(c)) return false;
    	visited.add(c);

    	for (Component output : c.getOutputs()) {
    		if (output instanceof Proposition) {
    			((Proposition) output).setValue(c.getValue());
    		}
    		if (!(output instanceof And) && isLatch(original, target, output, visited, false)) {
    			return true;
    		}
    	}
    	return false;
    }

    @Override
    public void getDeadStates(Role role) {
    	Set<Proposition> goalStates = propNet.getGoalPropositions().get(role);
    	Set<Component> visited = new HashSet<Component>();

    	for (Proposition p: latches) {
    		Integer score = 0;
    		if (isDeadState(p, score, goalStates, true, visited)) {
    			System.out.println("This is a dead state: " + p);
    			deadStates.put(p, score);
    		} else {
    			System.out.println("This is NOT a dead state: " + p);
    		}
    	}
    	System.out.println("Here are the dead states: " + deadStates);
    }

    private boolean isDeadState(Component c, Integer score, Set<Proposition> goalStates, boolean pos, Set<Component> visited) {
    	if (visited.contains(c)) return false;
    	visited.add(c);

    	if (c instanceof Proposition && goalStates.contains(c)) {
    		score = getGoalValue((Proposition) c);
    		return true;
    	}

    	for(Component output : c.getOutputs()) {
    		if ((pos && output instanceof And) || (!pos && output instanceof Or)) {
    			return false;
    		}
    		if (isDeadState(output, score, goalStates, output instanceof Not ? !pos : pos, visited)) {
    			return true;
    		}
    	}

    	return false;
    }

}