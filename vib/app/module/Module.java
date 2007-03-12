package vib.app.module;

import vib.app.gui.Console;

public class Module {
	protected static Console console;
	protected static String name, message;

	protected static void run(State state, int index) {
		throw new RuntimeException("programming error");
	}

	// at a later stage, these functions will schedule multi-threaded jobs
	public static void runOnOneImage(State state, int index) {
		console.append(message + ": " + index + "/" +
				state.getFileCount() + "\n");
		run(state, index);
	}

	public static void runOnAllImages(State state) {
		for (int i = 0; i < state.getFileCount(); i++)
			runOnOneImage(state, i);
	}

	public static void runOnAllImagesAndTemplate(State state) {
		for (int i = -1; i < state.getFileCount(); i++)
			runOnOneImage(state, i);
	}
}
