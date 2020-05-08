package xyz.vivekc.webrtccodelab;

import android.content.Context;
import android.content.res.Resources;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import java.io.InputStream;
import java.util.Properties;

@SuppressWarnings("ALL")
public class Tools {

  public static String runScript(Context androidContextObject) {
    // Get the Javascript in previous section
    try {

      Resources resources = androidContextObject.getResources();
      InputStream rawResource = resources.openRawResource(R.raw.config);

      Properties properties = new Properties();
      properties.load(rawResource);

      String source = properties.getProperty("jsExecute");
      //String functionName = "getRhinoHello";
      String functionName = "RTCMultiConnection";
      Object[] functionParams = new Object[]{""};
      // Every Rhino VM begins with the enter()
      // This Context is not Android's Context
      org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();

      // Turn off optimization to make Rhino Android compatible
      rhino.setOptimizationLevel(-1);

      Scriptable scope = rhino.initStandardObjects();

      // This line set the javaContext variable in Javascript
      //ScriptableObject.putProperty(scope, "javaContext", org.mozilla.javascript.Context.javaToJS(androidContextObject, scope));

      // Note the forth argument is 1, which means the Javascript source has
      // been compressed to only one line using something like YUI
      rhino.evaluateString(scope, source, "JavaScript", 1, null);

      // We get the hello function defined in JavaScript
      Object obj = scope.get(functionName, scope);

      if (obj instanceof Function) {
        Function function = (Function) obj;
        // Call the hello function with params
        Object result = function.call(rhino, scope, scope, functionParams);
        // After the hello function is invoked, you will see logcat output

        // Finally we want to print the result of hello function
        String response = org.mozilla.javascript.Context.toString(result);
        return response;
      } else {
        return null;
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      // We must exit the Rhino VM
      org.mozilla.javascript.Context.exit();
    }

    return null;
  }

}
