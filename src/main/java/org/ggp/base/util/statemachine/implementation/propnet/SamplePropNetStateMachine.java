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
import org.ggp.base.util.propnet.architecture.components.Proposition;
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

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
    	System.out.println("plz work");
        try {
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            ordering = getOrdering();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
    	markBases(state);
    	System.out.println("propmarkp from isTerminal");
    	return propmarkp(true, propNet.getTerminalProposition());
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
        markBases(state);
        List<Role> roles = propNet.getRoles();
        Set<Proposition> rewards = new HashSet<Proposition>();
        for (int i = 0; i < roles.size(); i++) {
        	if (roles.get(i).equals(role)) {
        		rewards = propNet.getGoalPropositions().get(role);
        		break;
        	}
        }
        for (Proposition p : rewards) {
        	System.out.println("propmarkp from getGoal");
        	if (propmarkp(true, p)) {
        		return getGoalValue(p);
        	}
        }
        return 0;
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
    	System.out.println("before; " + getStateFromBase());

    	clearPropnet();

    	System.out.println("after: " +getStateFromBase());

    	propNet.getInitProposition().setValue(true);
    	//System.out.println("init " + propNet.getInitProposition());
    	//clearPropnet();
    	//System.out.println(getStateFromBase());
    	//propNet.getInitProposition().setValue(true);
    	//System.out.println(getStateFromBase());
    	//int oi = 1/0;
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
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
    	// System.out.println("Getting legal moves in the propnet");
    	markBases(state);
    	List<Role> roles = propNet.getRoles();
    	Set<Proposition> legals = new HashSet<Proposition>();
    	for (int i = 0; i < roles.size(); i++) {
    		if (roles.get(i).equals(role)) {
    			legals = propNet.getLegalPropositions().get(role);
    			// System.out.println("legal propositions for this role: " + legals);
    		}
    	}
    	List<Move> actions = new ArrayList<Move>();
    	for (Proposition p : legals) {
    		// System.out.println("Does this prop get included? ");
    		System.out.println("propmarkp from getLegalMoves");
    		if (propmarkp(true, p)) {
    			// System.out.println("yes!");
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
    	//System.out.println("Get next state called");
    	// use the previous state and new moves
    	//System.out.println("moves" + moves);
    	markActions(moves);
        markBases(state);

    	HashMap<GdlSentence, Proposition> bases = (HashMap<GdlSentence, Proposition>) propNet.getBasePropositions();
    	Set<GdlSentence> sentences = bases.keySet();
    	//HashMap<GdlSentence, Boolean> newbases = new HashMap<GdlSentence, Boolean>();

    	for (GdlSentence sentence : sentences) {
    		//System.out.println("propmarkp from getNextState");
    		Component c = bases.get(sentence).getSingleInput().getSingleInput();
    		((Proposition) c).setValue(propmarkp(true, c));
    	}

        return getStateFromBase();


    	/*
    	for(GdlSentence s: sentences) {
    		System.out.println("old: "+ bases.get(s).getValue() + " | new: " + newbases.get(s));
    	}

    	Set<GdlSentence> newsentences = newbases.keySet();
    	for(GdlSentence sentence: newsentences) {
    		bases.get(sentence).setValue(newbases.get(sentence));
    	}

    	return getStateFromBase();
    	*/
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

        // All of the components in the PropNet
        List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // Compute the topological ordering.
        for (Component c : components) {
        	if (propositions.contains(c)) {
	        	int i = 0;
	        	for (i = 0; i < order.size(); i++) {
	        		Set<Component> outputs = order.get(i).getOutputs();
	        		for (Component output : outputs) {
	        			if (output.getOutputs().contains(c)) {
	        				break;
	        			}
	        		}
	        	}
	        	order.add(i, (Proposition) c);
        	}
        }

        return order;
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    private void markBases(MachineState state) {
    	Set<GdlSentence> sentences = state.getContents();
    	HashMap<GdlSentence, Proposition> basesMap = (HashMap<GdlSentence, Proposition>) propNet.getBasePropositions();
    	Set<GdlSentence> bases = basesMap.keySet();

    	for (GdlSentence base : bases) {
    		basesMap.get(base).setValue(sentences.contains(base));
    	}
    }

    private void markActions(List<Move> moves) {
    	List<GdlSentence> doeses = toDoes(moves);

    	HashMap<GdlSentence, Proposition> inputMap = (HashMap<GdlSentence, Proposition>) propNet.getInputPropositions();
    	Set<GdlSentence> inputs = inputMap.keySet();

    	for (GdlSentence input : inputs) {
    		inputMap.get(input).setValue(doeses.contains(input));
    	}
    }

    private void clearPropnet() {
    	HashMap<GdlSentence, Proposition> bases = (HashMap<GdlSentence, Proposition>) propNet.getBasePropositions();
    	Set<GdlSentence> sentences = bases.keySet();
    	for (GdlSentence sentence : sentences) {
    		Component c = bases.get(sentence).getSingleInput().getSingleInput();
    		((Proposition) c).setValue(false);
    	}
    }

    private boolean propmarkp(boolean isProp, Component c) {
    	//System.out.println("propmarkp on " + c);

    	if (propNet.getInitProposition().equals(c)) {
    		//System.out.println("special case of init proposition in propmarkp");
    		return c.getValue();
    	}
    	if (isProp) {
    		// base proposition
    		HashMap<GdlSentence, Proposition> baseMap = (HashMap<GdlSentence, Proposition>) propNet.getBasePropositions();
    		Set<GdlSentence> baseSentences = baseMap.keySet();
    		for (GdlSentence baseSentence: baseSentences) {
    			//System.out.println(c + " == " + baseMap.get(baseSentence) + " ? " + baseMap.get(baseSentence).equals(c));
    			if (baseMap.get(baseSentence).equals(c)) {
    				return c.getValue();
    			}
    		}
    		//System.out.println("Is NOT a base proposition");

    		// input proposition
    		HashMap<GdlSentence, Proposition> inputMap = (HashMap<GdlSentence, Proposition>) propNet.getInputPropositions();
    		Set<GdlSentence> inputSentences = inputMap.keySet();
    		for (GdlSentence inputSentence: inputSentences) {
    			//System.out.println(c + " == " + inputMap.get(inputSentence) + " ? " + inputMap.get(inputSentence).equals(c));
    			if (inputMap.get(inputSentence).equals(c)) {
    				return c.getValue();
    			}
    		}
    		//System.out.println("Is NOT an input proposition");

    		// view proposition
    		//System.out.println("propmarkp recursively from propmarkp");
        	return propmarkp(false, c.getSingleInput());
    	} else {
    		return c.getValue();
    	}
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
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
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
}