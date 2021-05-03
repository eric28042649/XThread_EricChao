//import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.StringTokenizer;
import java.lang.String;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Value;
import com.sun.jdi.Location;
import com.sun.jdi.Field;

import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;

import com.sun.jdi.request.*;

import com.sun.jdi.event.*;
// import com.sun.jdi.event.ClassPrepareEvent;
// import com.sun.jdi.event.LocatableEvent;
// import com.sun.jdi.event.EventSet;
// import com.sun.jdi.event.Event;
// import com.sun.jdi.event.StepEvent;
// import com.sun.jdi.event.BreakpointEvent;
// import com.sun.jdi.event.ThreadStartEvent;
// import com.sun.jdi.event.ThreadDeathEvent;
//import com.sun.jdi.event.EventRequestManager;


public class JDIExampleDebugger {
    private Class debugClass;
    private Vector<Integer> newBreakPointLines = new Vector<Integer>();

    public static void main(String[] args) throws Exception {
        JDIExampleDebugger debuggerInstance = new JDIExampleDebugger();
        debuggerInstance.setDebugClass(JDIExampleDebuggee.class);
        VirtualMachine vm = null;
        try {
            vm = debuggerInstance.connectAndLaunchVM();
            debuggerInstance.enableClassPrepareRequest(vm);
            EventSet eventSet = null;
            setEventRequests(vm);
            while ((eventSet = vm.eventQueue().remove()) != null) {
                for (Event event : eventSet) {
                    if (event instanceof ClassPrepareEvent) {
                        debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent)event);
                    }
                    if (event instanceof BreakpointEvent) {
                        debuggerInstance.enableStepRequest(vm, (BreakpointEvent)event);
                    }
                    if (event instanceof ThreadStartEvent) {
                        debuggerInstance.threadStart(vm, (ThreadStartEvent) event);
                    }
                    if (event instanceof ThreadDeathEvent) {
                        debuggerInstance.threadDeath(vm, (ThreadDeathEvent) event);
                    }
                    if (event instanceof StepEvent) {
                        debuggerInstance.displayVariables((StepEvent) event);
                    }
                    vm.resume();
                }
            }
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setEventRequests(VirtualMachine vm) {
        EventRequestManager erm = vm.eventRequestManager();
        ThreadStartRequest tsr = erm.createThreadStartRequest();
        tsr.enable();
        ThreadDeathRequest tdr = erm.createThreadDeathRequest();
        tdr.enable();
    }

    public void threadStart(VirtualMachine vm, ThreadStartEvent event) {
        System.out.println("Thread " + event.thread().name() + " Start\n");
    }

    public void threadDeath(VirtualMachine vm, ThreadDeathEvent event) {
        System.out.println("Thread " + event.thread().name() + " Finish\n");
    }

    private void setDebugClass(Class<JDIExampleDebuggee> debugClass) {
        this.debugClass = debugClass;
    }

    // create and launch a virtual machine
    public VirtualMachine connectAndLaunchVM() throws Exception {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        return (VirtualMachine) launchingConnector.launch(arguments);
    }

    // Request VM to trigger event when Debuggee is prepared.
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }
    
    public void enableStepRequest(VirtualMachine vm, BreakpointEvent event) {
        if (event.location().toString().
            contains(debugClass.getName() + ":" + newBreakPointLines.get(newBreakPointLines.size()-1) )) {
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(event.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
            stepRequest.enable();    
        }
    }

    public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException {
        ClassType classType = (ClassType) event.referenceType();
        for( Location location : classType.allLineLocations())
        {
            if(classType.locationsOfLine(location.lineNumber()).get(0).method().toString().contains("main(java.lang.String[])") )
            {
                newBreakPointLines.add(location.lineNumber());
                break;
            }
        }
        for(int lineNumber: newBreakPointLines) {
            Location location = classType.locationsOfLine(lineNumber).get(0);
            BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
            bpReq.enable();
        }
    }

    // print all the visible variables with the respective values
    public void displayVariables(LocatableEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
        List<StackFrame> frames = event.thread().frames();
        for (StackFrame stackFrame : frames) {
            if(stackFrame.location().toString().contains(debugClass.getName())) {
                Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
                System.out.println("Variables at " + stackFrame.location().toString() +  " > ");
                for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                    System.out.println(entry.getKey().name() + " = " + entry.getValue());
                }
                System.out.println();
            }
            // else {
            //     List<LocalVariable> lvs = stackFrame.visibleVariables();
            //     System.out.println("Variables at " + stackFrame.location().toString() +  " > ");
            //     for (LocalVariable lv : lvs) {
            //         System.out.println(lv.name() + " = "  + stackFrame.getValue(lv));
            //     }
            //     // Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
            //     // System.out.println("Variables at " + stackFrame.location().toString() +  " > ");
            //     // for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
            //     //     System.out.println(entry.getKey().name() + " = " + entry.getValue());
            //     // }
            //     System.out.println();
            // }
        }
    }
}