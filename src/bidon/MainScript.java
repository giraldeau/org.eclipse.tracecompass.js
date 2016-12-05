package bidon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

public class MainScript {
	
	private static final String JAVA_SCRIPT = "JavaScript";

	/*
	public enum EventFields {
		PREV_PID,
		NEXT_PID,
		PREV_COMM,
		NEXT_COMM,
	};
	*/
	
	public static class Event {
		private final int fPrevTid;
		private final int fNextTid;
		public Event(int prevTid, int nextTid) {
			fPrevTid = prevTid;
			fNextTid = nextTid;
		}
		public String getName() {
			return "sched_switch";
		}
		public int getPrevTid() {
			return fPrevTid;
		}
		public int getNextTid() {
			return fNextTid;
		}
	}
	
	public static class Trace {
		private final ArrayList<Event> fEvents = new ArrayList<>();
		private Iterator<Event> fIter; 
		
		public Trace() {
		}
			
		public void rewind() {
			fIter = fEvents.iterator();
		}
		
		public Event getNext() {
			if (fIter == null) {
				rewind();
			}
			return fIter.next();
		}
		
		public boolean hasNext() {
			if (fIter == null) {
				rewind();
			}
			return fIter.hasNext();
		}
		public void add(Event event) {
			fEvents.add(event);
		}
	}
	
	public static Trace generateStubTrace(int length) {
		Trace trace = new Trace();
		for (int i = 0; i < length; i++) {
			trace.add(new Event(i, i + 1));
		}
		return trace;
	}
	
	public static abstract class Experiment {
		protected Trace fTrace;
		protected ScriptEngine fEngine;
		public Experiment(ScriptEngine engine) {
			fEngine = engine;
			fTrace = generateStubTrace(1000000);
		}
		public void before() {
			fTrace.rewind();
		}
		public void go() throws NoSuchMethodException, ScriptException {
		}
	}
	
	public static class ExperimentInvokeForEach extends Experiment {
		public ExperimentInvokeForEach(ScriptEngine engine) {
			super(engine);
		}
		
		@Override
		public void go() throws NoSuchMethodException, ScriptException {
			Invocable inv = (Invocable) fEngine;
			
			/* invoke a method for each event */
			Bindings result = (Bindings) fEngine.get("result");
			Double c1 = (Double) result.get("count");
			Object handler = fEngine.get("handler");
			while (fTrace.hasNext()) {
				inv.invokeMethod(handler, "handleEvent", fTrace.getNext());
			}
			Double c2 = (Double) result.get("count");
			if (c1 + 1000000 != c2) {
				System.out.println("houston, got a problem");
			}
		}
	}

	public static class ExperimentInvokeOnce extends Experiment {
		public ExperimentInvokeOnce(ScriptEngine engine) {
			super(engine);
		}

		@Override
		public void go() throws NoSuchMethodException, ScriptException {
			Invocable inv = (Invocable) fEngine;
			
			/* invoke a method for each event */
			Bindings result = (Bindings) fEngine.get("result");
			Double c1 = (Double) result.get("count");
			Object module = fEngine.get("module");
			inv.invokeMethod(module, "process", fTrace);
			Double c2 = (Double) result.get("count");
			if (c1 + 1000000 != c2) {
				System.out.println("houston, got a problem");
			}
		}
	}

	public static class ExperimentRunner {
		public void execute(Experiment ex) {
			long elapsed = 0;
			long repeat = 0;
			while(elapsed < 1E9 || repeat < 10) {
				ex.before();
				long t1 = System.nanoTime();
				try {
					ex.go();
				} catch (NoSuchMethodException | ScriptException e) {
					e.printStackTrace();
					break;
				}
				long t2 = System.nanoTime();
				long diff = t2 - t1;
				elapsed += diff;
				repeat++;
			}
			double ns = (double)elapsed / (repeat * 1E9 * 1E6);
			
			System.out.println(String.format("repeat:%d elapsed:%.9f", repeat, ns));
		}
	}
	
	public static void main(String[] args) {
		
		System.out.println(Arrays.toString(args));
		
		if (args.length < 1) {
			return;
		}
		
		String scriptPath = args[0];
		File scriptFile = new File(scriptPath);
		
		if (!scriptFile.exists()) {
			System.out.println("script not found: " + scriptFile);
			System.out.println(new File(".").getAbsolutePath());
			return;
		}
		
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName(JAVA_SCRIPT);
		ArrayList<Experiment> experiments = new ArrayList<>();
		experiments.add(new ExperimentInvokeForEach(engine));
		experiments.add(new ExperimentInvokeOnce(engine));
		ExperimentRunner runner = new ExperimentRunner();
		
		try (BufferedReader reader = new BufferedReader(new FileReader(scriptFile))) {
			/* load the script */
			engine.eval(reader);
			for (Experiment exp: experiments) {
				System.out.println("running experiment: " + exp.getClass());
				runner.execute(exp);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ScriptException e) {
			e.printStackTrace();
		}
		System.out.println("done");
	}
	
}
