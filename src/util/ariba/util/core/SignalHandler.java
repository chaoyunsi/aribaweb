/*
    Copyright 1996-2008 Ariba, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    $Id: //ariba/platform/util/core/ariba/util/core/SignalHandler.java#3 $
*/

package ariba.util.core;

import ariba.util.log.Log;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Wrapper around the sun.misc.SignalHandler to avoid having the rest
 * of the application to depend on this undocumented class and as such
 * can be changed in any Java release.
 * 
 * Note that this implementation guarantees that if another handler was registered 
 * for the same signal, it will be invoked after this one. In other words, you
 * can register multiple handlers for the same signal in the reverse order
 * in which you want them to be invoked.  
 * @aribaapi ariba
 */
public abstract class SignalHandler
{        
    /**
     * Registers a new SignalHandler for the given signal name
     * <b>Note:</b> this method is not thread safe<br/>
     * @param className name of the subclass of SignalHandler
     * @param signalName the name of the signal handled
     * @return the newly registered SignalHandler, or <code>null</code>
     * if the registration did not succeed.
     * @aribaapi ariba
     */
    public static SignalHandler registerSignalHandler (String className, 
                                                       String signalName)
    {
        SignalHandler handler =
            (SignalHandler)ClassUtil.newInstance(className, SignalHandler.class, true);
        if (handler == null) {
            Log.util.warning(9157, className);
            return null;
        }
        return registerSignalHandler(handler, signalName);
    }
    
    /**
     * Registers the given SignalHandler for the given signal
     * <b>Note:</b> an handler can be registered for only one signal !<br/>
     * <b>Note:</b> this method is not thread safe<br/>
     * @param handler the handler to register
     * @param signalName the signal to register with
     * @return the handler which has been registered or <code>null</code
     * if the registration did not succeed.
     * @aribaapi ariba
     */
    public static SignalHandler registerSignalHandler (SignalHandler handler,
                                                       String signalName)
    {
        // Note: synchronizing the access to _oldHandler should be
        // done to prevent two threads from registering the same handler
        // but it seems overkill so I'm not doing it.
        if (handler._oldHandler != null) {
            Log.util.warning(9158, handler, signalName);
            return null;
        }
        try {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
            Object signal = signalClass.getConstructor(String.class).newInstance(signalName);
            
            Object proxy = Proxy.newProxyInstance(
                SignalHandler.class.getClassLoader(),
                new Class<?>[] { signalHandlerClass },
                new InvocationHandler() {
                    public Object invoke (Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("handle")) {
                            Object sig = args[0];
                            String name = (String)signalClass.getMethod("getName").invoke(sig);
                            handler.handleSignal(name, sig);
                        }
                        return null;
                    }
                }
            );

            Method handleMethod = signalClass.getMethod("handle", signalClass, signalHandlerClass);
            handler._oldHandler = handleMethod.invoke(null, signal, proxy);
        }
        catch (Throwable e) {
            Log.util.warning(9159, handler, signalName, e.getMessage());
            return null;
        }
        return handler;
    }
    
    /**
     * Raises a signal in the current process.
     * @param signalName the name of the signal to raise
     * @aribaapi ariba
     */
    public static void raiseSignal (String signalName)
    {
        try {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Object signal = signalClass.getConstructor(String.class).newInstance(signalName);  
            Method raiseMethod = signalClass.getMethod("raise", signalClass);
            raiseMethod.invoke(null, signal);
        }
        catch (Throwable e) {
            Log.util.warning(9160, signalName, e.getMessage());
        }
    }

    /**
     * The previous handler registered for the same signal
     */
    private Object _oldHandler;
    
    /**
     * Specifies whether the previous handler should
     * receive the signal after this handler. 
     */
    private boolean _followSignalChain = true;
    
    /**
     * Specifies whether the previous handler should
     * receive the signal after this handler.
     * @aribaapi ariba 
     */
    protected void setFollowSignalChain (boolean value)
    {
        _followSignalChain = value;
    }
    
    /**
     * Method which is called to handle a signal.
     * Our implementation guarantees that if another handler was registered 
     * for the same signal, it will be invoked after this.
     * @aribaapi private
     */
    final void handleSignal (String name, Object signal)
    {
        Log.util.info(9161, name);
        try {
            handle(name);
        }
        finally {
            // Chain back to previous handler, if one exists
            try {
                if (_followSignalChain && _oldHandler != null) {
                    Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
                    Object sigDfl = signalHandlerClass.getField("SIG_DFL").get(null);
                    Object sigIgn = signalHandlerClass.getField("SIG_IGN").get(null);
                    if (_oldHandler != sigDfl && _oldHandler != sigIgn) {
                        Method handleMethod = signalHandlerClass.getMethod("handle", Class.forName("sun.misc.Signal"));
                        handleMethod.invoke(_oldHandler, signal);
                    }
                }
            }
            catch (Throwable t) {
                // Ignore
            }
        }        
    }

    /**
     * Handles the given signal
     * @param signalName the name of the signal
     * @aribaapi ariba
     */
    protected abstract void handle (String signalName);    
}
