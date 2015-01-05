/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.Transformer;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.*;
import org.gradle.nativeplatform.toolchain.internal.compilespec.*;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.Map;

class VisualCppPlatformToolProvider implements PlatformToolProvider {
    private final OperatingSystem operatingSystem;
    private final Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations;
    private final VisualCppInstall visualCpp;
    private final WindowsSdk sdk;
    private final NativePlatformInternal targetPlatform;
    private final ExecActionFactory execActionFactory;
    private final String outputFileSuffix;

    VisualCppPlatformToolProvider(OperatingSystem operatingSystem, Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations, VisualCppInstall visualCpp, WindowsSdk sdk, NativePlatformInternal targetPlatform, ExecActionFactory execActionFactory) {
        this.operatingSystem = operatingSystem;
        this.commandLineToolConfigurations = commandLineToolConfigurations;
        this.visualCpp = visualCpp;
        this.sdk = sdk;
        this.targetPlatform = targetPlatform;
        this.outputFileSuffix = "." + getObjectFileExtension();
        this.execActionFactory = execActionFactory;
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {
    }

    public String getObjectFileExtension() {
        return "obj";
    }

    public String getExecutableName(String executablePath) {
        return operatingSystem.getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryName) {
        return operatingSystem.getSharedLibraryName(libraryName);
    }

    public String getStaticLibraryName(String libraryName) {
        return operatingSystem.getStaticLibraryName(libraryName);
    }

    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName).replaceFirst("\\.dll$", ".lib");
    }

    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(T spec) {
        if (spec instanceof CppCompileSpec) {
            return castCompiler(createCppCompiler());
        }
        if (spec instanceof CCompileSpec) {
            return castCompiler(createCCompiler());
        }
        if (spec instanceof ObjectiveCppCompileSpec) {
            throw new RuntimeException("Objective-C++ is not available on the Visual C++ toolchain");
        }
        if (spec instanceof ObjectiveCCompileSpec) {
            throw new RuntimeException("Objective-C is not available on the Visual C++ toolchain");
        }
        if (spec instanceof WindowsResourceCompileSpec) {
            return castCompiler(createWindowsResourceCompiler());
        }
        if (spec instanceof AssembleSpec) {
            return castCompiler(createAssembler());
        }
        if (spec instanceof LinkerSpec) {
            return castCompiler(createLinker());
        }
        if (spec instanceof StaticLibraryArchiverSpec) {
            return castCompiler(createStaticLibraryArchiver());
        }
        throw new IllegalArgumentException(String.format("Don't know how to compile from a spec of type %s.", spec.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    private <T extends CompileSpec> Compiler<T> castCompiler(Compiler<?> compiler) {
        return (Compiler<T>) compiler;
    }

    public Compiler<CppCompileSpec> createCppCompiler() {
        CommandLineTool commandLineTool = tool("C++ compiler", visualCpp.getCompiler(targetPlatform));
        CppCompiler cppCompiler = new CppCompiler(commandLineTool, invocation(commandLineToolConfigurations.get(ToolType.CPP_COMPILER)), addIncludePathAndDefinitions(CppCompileSpec.class), outputFileSuffix, true);
        return new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, outputFileSuffix);
    }

    public Compiler<CCompileSpec> createCCompiler() {
        CommandLineTool commandLineTool = tool("C compiler", visualCpp.getCompiler(targetPlatform));
        CCompiler cCompiler = new CCompiler(commandLineTool, invocation(commandLineToolConfigurations.get(ToolType.C_COMPILER)), addIncludePathAndDefinitions(CCompileSpec.class), outputFileSuffix, true);
        return new OutputCleaningCompiler<CCompileSpec>(cCompiler, outputFileSuffix);
    }

    public Compiler<AssembleSpec> createAssembler() {
        CommandLineTool commandLineTool = tool("Assembler", visualCpp.getAssembler(targetPlatform));
        return new Assembler(commandLineTool, invocation(commandLineToolConfigurations.get(ToolType.ASSEMBLER)));
    }

    public Compiler<WindowsResourceCompileSpec> createWindowsResourceCompiler() {
        CommandLineTool commandLineTool = tool("Windows resource compiler", sdk.getResourceCompiler(targetPlatform));
        WindowsResourceCompiler windowsResourceCompiler = new WindowsResourceCompiler(commandLineTool, invocation(commandLineToolConfigurations.get(ToolType.WINDOW_RESOURCES_COMPILER)), addIncludePathAndDefinitions(WindowsResourceCompileSpec.class));
        return new OutputCleaningCompiler<WindowsResourceCompileSpec>(windowsResourceCompiler, ".res");
    }

    public Compiler<LinkerSpec> createLinker() {
        CommandLineTool commandLineTool = tool("Linker", visualCpp.getLinker(targetPlatform));
        return new LinkExeLinker(commandLineTool, invocation(commandLineToolConfigurations.get(ToolType.LINKER)), addLibraryPath());
    }

    public Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
        CommandLineTool commandLineTool = tool("Static library archiver", visualCpp.getArchiver(targetPlatform));
        return new LibExeStaticLibraryArchiver(commandLineTool, invocation(commandLineToolConfigurations.get(ToolType.STATIC_LIB_ARCHIVER)));
    }

    private CommandLineTool tool(String toolName, File exe) {
        return new DefaultCommandLineTool(toolName, exe, execActionFactory);
    }

    private CommandLineToolInvocation invocation(CommandLineToolConfigurationInternal commandLineToolConfiguration) {
        MutableCommandLineToolInvocation invocation = new DefaultCommandLineToolInvocation();
        // The visual C++ tools use the path to find other executables
        // TODO:ADAM - restrict this to the specific path for the target tool
        invocation.addPath(visualCpp.getPath(targetPlatform));
        invocation.addPath(sdk.getBinDir(targetPlatform));
        // Clear environment variables that might effect cl.exe & link.exe
        clearEnvironmentVars(invocation, "INCLUDE", "CL", "LIBPATH", "LINK", "LIB");

        invocation.addPostArgsAction(commandLineToolConfiguration.getArgAction());
        return invocation;
    }

    private void clearEnvironmentVars(MutableCommandLineToolInvocation invocation, String... names) {
        // TODO:DAZ This check should really be done in the compiler process
        Map<String, ?> environmentVariables = Jvm.current().getInheritableEnvironmentVariables(System.getenv());
        for (String name : names) {
            Object value = environmentVariables.get(name);
            if (value != null) {
                VisualCppToolChain.LOGGER.warn("Ignoring value '{}' set for environment variable '{}'.", value, name);
                invocation.addEnvironmentVar(name, "");
            }
        }
    }

    // TODO:DAZ These should be modelled properly, not hidden in a compile spec transformation
    private <T extends NativeCompileSpec> Transformer<T, T> addIncludePathAndDefinitions(Class<T> type) {
        return new Transformer<T, T>() {
            public T transform(T original) {
                original.include(visualCpp.getIncludePath(targetPlatform));
                original.include(sdk.getIncludeDirs());
                for (Map.Entry<String, String> definition : visualCpp.getDefinitions(targetPlatform).entrySet()) {
                    original.define(definition.getKey(), definition.getValue());
                }
                return original;
            }
        };
    }

    private Transformer<LinkerSpec, LinkerSpec> addLibraryPath() {
        return new Transformer<LinkerSpec, LinkerSpec>() {
            public LinkerSpec transform(LinkerSpec original) {
                original.libraryPath(visualCpp.getLibraryPath(targetPlatform), sdk.getLibDir(targetPlatform));
                return original;
            }
        };
    }
}
