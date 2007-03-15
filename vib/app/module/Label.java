package vib.app.module;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.image.IndexColorModel;

import vib.app.Options;

import vib.segment.CustomStackWindow;

import ij.ImagePlus;

public class Label extends Module {
	protected String getName() { return "Label"; }
	protected String getMessage() { return "Labelling"; }

	protected void run(State state, int index) {
		new SplitChannels().runOnOneImage(state, index);

		prereqsDone(state, index);

		int refChannel = state.options.refChannel - 1;
		String imagePath = state.getImagePath(refChannel, index);
		String labelPath = state.getImagePath(-1, index);
		if (state.upToDate(imagePath, labelPath))
			return;

		ImagePlus image = state.getImage(imagePath);
		setGrayLUT(image);
		ImagePlus labels = state.getImage(labelPath);

		ActionListener a = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(e.getActionCommand().equals("Ok")) {
					synchronized (this) {
						notifyAll();
					}
				}
			}
		};

		
		CustomStackWindow csw = new CustomStackWindow(image);
		csw.setLabels(labels);
		csw.addActionListener(a);
		synchronized (a) {
			try {
				a.wait();
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		labels = csw.getLabels();
		csw.cleanUp();
		state.save(labels, labelPath);
	}

	private static void setGrayLUT(ImagePlus ip) {
		byte[] channel = new byte[256];
		for (int i = 0; i < 256; i++)
			channel[i] = (byte)i;
		IndexColorModel c = new IndexColorModel(8, 256,
			channel, channel, channel);
                ip.getProcessor().setColorModel(c);
                if (ip.getStackSize() > 1)
                        ip.getStack().setColorModel(c);

	}
}
