package com.smartvillager.village;

/**
 * Whether a village is currently running full real-time simulation or
 * abstract batch simulation.
 *
 * FULL  — player is within ~128 blocks; chunks are loaded; villagers physically
 *         move, pathfind, and execute Brain tasks in real time.
 *
 * ABSTRACT — player is beyond ~128 blocks; village chunks are unloaded; state
 *            is tracked as data and updated every ~60 seconds via a batch
 *            calculation that approximates what would have happened.
 *
 * Mode is determined at runtime from player proximity and is NOT persisted.
 * A village always loads from disk in ABSTRACT mode and transitions to FULL
 * when a player enters range.
 */
public enum SimulationMode {
    FULL,
    ABSTRACT
}
