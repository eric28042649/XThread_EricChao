import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
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
import com.sun.jdi.ThreadReference;

import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;

import com.sun.jdi.request.*;

import com.sun.jdi.event.*;

public class JDIExampleDebugger {
    private Class debugClass;
    private Vector<Integer> newBreakPointLines = new Vector<Integer>();
    private static String[] threadStep = new String[3];
    private static int deathCount = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("Please input thread step:");
        Scanner input = new Scanner(System.in);
        String tmp = input.nextLine();
        for (int i = 0; i < 3; i++) {
            threadStep[i] = "Thread-"+tmp.substring(i , (i + 1));
            // System.out.print(threadStep[i] + "\t");
        }
        System.out.println();

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
                        debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent) event);
                    }
                    if (event instanceof BreakpointEvent) {
                        debuggerInstance.enableStepRequest(vm, (BreakpointEvent) event);
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
        if (event.thread().name().equals(threadStep[deathCount])) {
            event.thread().resume();
            System.out.println(event.thread().name() + " Start\n");
        } else {
            for (int i = deathCount + 1; i < threadStep.length; i++) {
                if (event.thread().name().equals(threadStep[i])) {
                    event.thread().suspend();
                    System.out.println("Suspend " + event.thread().name()+"\n");
                }
            }
        }

    }

    public void threadDeath(VirtualMachine vm, ThreadDeathEvent event) {
        System.out.println( event.thread().name() + " Finish\n");
        if (deathCount < threadStep.length-1 && event.thread().name().equals(threadStep[deathCount])) {
            deathCount++;
            if (deathCount < threadStep.length) {
                for (ThreadReference tr : vm.allThreads()) {
                    if (tr.name().equals(threadStep[deathCount])) {
                        tr.resume();
                        System.out.println("resume " + tr.name()+"\n");
                        break;
                    }
                }
            } 
        }

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
        if (event.location().toString()
                .contains(debugClass.getName() + ":" + newBreakPointLines.get(newBreakPointLines.size() - 1))) {
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(event.thread(), StepRequest.STEP_LINE,
                    StepRequest.STEP_OVER);
            stepRequest.enable();
        }
    }

    public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException {
        ClassType classType = (ClassType) event.referenceType();
        for (Location location : classType.allLineLocations()) {
            if (classType.locationsOfLine(location.lineNumber()).get(0).method().toString()
                    .contains("main(java.lang.String[])")) {
                newBreakPointLines.add(location.lineNumber());
                break;
            }
        }
        for (int lineNumber : newBreakPointLines) {
            Location location = classType.locationsOfLine(lineNumber).get(0);
            BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
            bpReq.enable();
        }
    }

    // print all the visible variables with the respective values
    public void displayVariables(LocatableEvent event)
            throws IncompatibleThreadStateException, AbsentInformationException {
        List<StackFrame> frames = event.thread().frames();
        for (StackFrame stackFrame : frames) {
            if (stackFrame.location().toString().contains(debugClass.getName())) {
                Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
                //System.out.println("Variables at " + stackFrame.location().toString() + " > ");
                for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                    //System.out.println(entry.getKey().name() + " = " + entry.getValue());
                }
                //System.out.println();
            }
        }
    }
}