package org.factcast.script.engine.graaljs;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import javax.script.ScriptException;
import lombok.extern.slf4j.Slf4j;
import org.factcast.script.engine.Engine;
import org.factcast.script.engine.exception.ScriptEngineException;

import static org.factcast.script.engine.graaljs.NashornCompatContextBuilder.CTX;

@Slf4j
public class GraalJSEngine implements Engine {

  private static final Object ENGINE_CREATION_MUTEX = new Object();

  private GraalJSScriptEngine engine;

  GraalJSEngine() {
    // not stated anywhere that engine creation is thread-safe
    synchronized (ENGINE_CREATION_MUTEX) {
      this.engine = GraalJSScriptEngine.create(null, CTX);
    }
  }

  @Override
  public Engine warm(String script) throws ScriptEngineException {
    try {
      engine.compile(script).eval();
      return this;
    } catch (RuntimeException | ScriptException e) {
      log.debug("Exception during engine creation. Escalating.", e);
      throw new ScriptEngineException(e);
    }
  }

  @Override
  public Object invoke(String functionName, Object... input) throws ScriptEngineException {
    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(Truffle.class.getClassLoader());
    try {
      // if you have transformations that add new objects like "foo.bar = {}"
      // this will lead to have PolyglotMap's being created inside jsonAsMap
      // these Graal maps are bound to a Context which bound to a ScriptEngine in our case
      // jackson seems somehow to access fields of this map which leads to exceptions like
      // mentioned in https://github.com/factcast/factcast/issues/1506
      synchronized (engine) {
        return engine.invokeFunction(functionName, input);
      }
    } catch (RuntimeException | ScriptException | NoSuchMethodException e) {
      log.debug("Exception during the invocation of '{}'. Escalating.", functionName, e);
      throw new ScriptEngineException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }
  }

  @Override
  public Object eval(String script) throws ScriptEngineException {
    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(Truffle.class.getClassLoader());
    try {
      synchronized (engine) {
        return engine.eval(script);
      }
    } catch (RuntimeException | ScriptException e) {
      log.debug("Exception during the evaluation of '{}'. Escalating.", script, e);
      throw new ScriptEngineException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }
  }
}
