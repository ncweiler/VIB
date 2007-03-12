package vib.app.module;

import vib.app.Options;

public class TransformImages extends Module {
	protected final String name = "TransformImages";
	protected final String message = "Transforming images";

	public static void run(State state, int index) {
		Module module = null;
		switch (state.options.transformationMethod) {
		case Options.CENTER:
			module = new CenterTransformation();
			break;
		case Options.LABEL_DIFFUSION:
			module = new LabelDiffusionTransformation();
		default:
			throw new RuntimeException("invalid transformation");
		}
		module.runOnOneImage(state, index);
	}
}

