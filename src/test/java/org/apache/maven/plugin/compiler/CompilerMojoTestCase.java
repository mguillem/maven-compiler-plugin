package org.apache.maven.plugin.compiler;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.inject.Inject;

import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.inject.Provides;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.ToolchainManager;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.compiler.stubs.CompilerManagerStub;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.ArtifactStub;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MojoTest
public class CompilerMojoTestCase
{
    
    private String source = AbstractCompilerMojo.DEFAULT_SOURCE;

    private String target = AbstractCompilerMojo.DEFAULT_TARGET;

    @Inject
    private Session session;

    @Inject
    private ProjectManager projectManager;

    @Inject
    private ArtifactManager artifactManager;

    /**
     * tests the ability of the plugin to compile a basic file
     */
    @Test
    public void testCompilerBasic(
            @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-basic-test/plugin-config.xml"  )
            CompilerMojo compileMojo,

            @InjectMojo( goal = "testCompile", pom = "classpath:/unit/compiler-basic-test/plugin-config.xml"  )
            TestCompilerMojo testCompileMojo
    )
        throws Exception
    {
        configureMojo( compileMojo );

        Logger log = LoggerFactory.getLogger( compileMojo.getClass() );

        compileMojo.execute();

        File testClass = new File( compileMojo.getOutputDirectory(), "TestCompile0.class" );

        assertTrue( testClass.exists() );

        configureMojo( compileMojo, testCompileMojo );

        testCompileMojo.execute();

        Artifact projectArtifact = (Artifact) getVariableValueFromObject( compileMojo, "projectArtifact" );
        assertFalse( artifactManager.getPath( projectArtifact ).isPresent(), "MCOMPILER-94: artifact file should be null if there is nothing to compile" );

        testClass = new File( testCompileMojo.getOutputDirectory(), "TestCompile0Test.class" );

        verify( log ).warn( startsWith( "No explicit value set for target or release!" ) );

        assertTrue( testClass.exists() );
    }

    @Test
    @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-basic-sourcetarget/plugin-config.xml"  )
    public void testCompilerBasicSourceTarget( CompilerMojo compileMojo )
                    throws Exception
    {
        configureMojo( compileMojo );

        Logger log = LoggerFactory.getLogger( compileMojo.getClass() );

        compileMojo.execute();
        
        verify( log, never() ).warn( startsWith( "No explicit value set for target or release!" ) );
    }

    /**
     * tests the ability of the plugin to respond to empty source
     *
     * @throws Exception
     */
    @Test
    public void testCompilerEmptySource(
            @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-empty-source-test/plugin-config.xml"  )
            CompilerMojo compileMojo,

            @InjectMojo( goal = "testCompile", pom = "classpath:/unit/compiler-empty-source-test/plugin-config.xml"  )
            TestCompilerMojo testCompileMojo
    )
        throws Exception
    {
        configureMojo( compileMojo );

        compileMojo.execute();

        assertFalse( compileMojo.getOutputDirectory().exists() );

        Artifact projectArtifact = (Artifact) getVariableValueFromObject( compileMojo, "projectArtifact" );
        assertFalse( artifactManager.getPath( projectArtifact ).isPresent(), "MCOMPILER-94: artifact file should be null if there is nothing to compile" );

        configureMojo( compileMojo, testCompileMojo );

        testCompileMojo.execute();

        assertFalse( testCompileMojo.getOutputDirectory().exists() );
    }

    /**
     * tests the ability of the plugin to respond to includes and excludes correctly
     */
    @Test
    public void testCompilerIncludesExcludes(
            @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-includes-excludes-test/plugin-config.xml"  )
            CompilerMojo compileMojo,

            @InjectMojo( goal = "testCompile", pom = "classpath:/unit/compiler-includes-excludes-test/plugin-config.xml"  )
            TestCompilerMojo testCompileMojo
    )
        throws Exception
    {
        configureMojo( compileMojo );

        Set<String> includes = new HashSet<>();
        includes.add( "**/TestCompile4*.java" );
        setVariableValueToObject( compileMojo, "includes", includes );

        Set<String> excludes = new HashSet<>();
        excludes.add( "**/TestCompile2*.java" );
        excludes.add( "**/TestCompile3*.java" );
        setVariableValueToObject( compileMojo, "excludes", excludes );

        compileMojo.execute();

        File testClass = new File( compileMojo.getOutputDirectory(), "TestCompile2.class" );
        assertFalse( testClass.exists() );

        testClass = new File( compileMojo.getOutputDirectory(), "TestCompile3.class" );
        assertFalse( testClass.exists() );

        testClass = new File( compileMojo.getOutputDirectory(), "TestCompile4.class" );
        assertTrue( testClass.exists() );

        configureMojo( compileMojo, testCompileMojo );

        setVariableValueToObject( testCompileMojo, "testIncludes", includes );
        setVariableValueToObject( testCompileMojo, "testExcludes", excludes );

        testCompileMojo.execute();

        testClass = new File( testCompileMojo.getOutputDirectory(), "TestCompile2TestCase.class" );
        assertFalse( testClass.exists() );

        testClass = new File( testCompileMojo.getOutputDirectory(), "TestCompile3TestCase.class" );
        assertFalse( testClass.exists() );

        testClass = new File( testCompileMojo.getOutputDirectory(), "TestCompile4TestCase.class" );
        assertTrue( testClass.exists() );
    }

    /**
     * tests the ability of the plugin to fork and successfully compile
     */
    @Test
    @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-fork-test/plugin-config.xml"  )
    public void testCompilerFork(
            @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-fork-test/plugin-config.xml"  )
            CompilerMojo compileMojo,

            @InjectMojo( goal = "testCompile", pom = "classpath:/unit/compiler-fork-test/plugin-config.xml"  )
            TestCompilerMojo testCompileMojo
    )
        throws Exception
    {
        configureMojo( compileMojo );

        // JAVA_HOME doesn't have to be on the PATH.
        setVariableValueToObject( compileMojo, "executable",  new File( System.getenv( "JAVA_HOME" ), "bin/javac" ).getPath() );

        compileMojo.execute();

        File testClass = new File( compileMojo.getOutputDirectory(), "TestCompile1.class" );
        assertTrue( testClass.exists() );

        configureMojo( compileMojo, testCompileMojo );

        // JAVA_HOME doesn't have to be on the PATH.
        setVariableValueToObject( testCompileMojo, "executable",  new File( System.getenv( "JAVA_HOME" ), "bin/javac" ).getPath() );

        testCompileMojo.execute();

        testClass = new File( testCompileMojo.getOutputDirectory(), "TestCompile1TestCase.class" );
        assertTrue( testClass.exists() );
    }

    @Test
    public void testOneOutputFileForAllInput(
            @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-one-output-file-test/plugin-config.xml"  )
            CompilerMojo compileMojo,

            @InjectMojo( goal = "testCompile", pom = "classpath:/unit/compiler-one-output-file-test/plugin-config.xml"  )
            TestCompilerMojo testCompileMojo
    )
        throws Exception
    {
        configureMojo( compileMojo );

        setVariableValueToObject( compileMojo, "compilerManager", new CompilerManagerStub() );

        compileMojo.execute();

        File testClass = new File( compileMojo.getOutputDirectory(), "compiled.class" );
        assertTrue( testClass.exists() );

        configureMojo( compileMojo, testCompileMojo );

        setVariableValueToObject( testCompileMojo, "compilerManager", new CompilerManagerStub() );

        testCompileMojo.execute();

        testClass = new File( testCompileMojo.getOutputDirectory(), "compiled.class" );
        assertTrue( testClass.exists() );
    }

    @Test
    @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-args-test/plugin-config.xml"  )
    public void testCompilerArgs( CompilerMojo compileMojo )
        throws Exception
    {
         configureMojo( compileMojo );

        setVariableValueToObject( compileMojo, "compilerManager", new CompilerManagerStub() );

        compileMojo.execute();

        File testClass = new File( compileMojo.getOutputDirectory(), "compiled.class" );
        assertTrue( testClass.exists() );
        assertEquals( Arrays.asList( "key1=value1","-Xlint","-my&special:param-with+chars/not>allowed_in_XML_element_names" ), compileMojo.compilerArgs );
    }

    @Test
    public void testOneOutputFileForAllInput2(
            @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-one-output-file-test2/plugin-config.xml"  )
            CompilerMojo compileMojo,

            @InjectMojo( goal = "testCompile", pom = "classpath:/unit/compiler-one-output-file-test2/plugin-config.xml"  )
            TestCompilerMojo testCompileMojo
    )
        throws Exception
    {
        configureMojo( compileMojo );

        setVariableValueToObject( compileMojo, "compilerManager", new CompilerManagerStub() );

        Set<String> includes = new HashSet<>();
        includes.add( "**/TestCompile4*.java" );
        setVariableValueToObject( compileMojo, "includes", includes );

        Set<String> excludes = new HashSet<>();
        excludes.add( "**/TestCompile2*.java" );
        excludes.add( "**/TestCompile3*.java" );
        setVariableValueToObject( compileMojo, "excludes", excludes );

        compileMojo.execute();

        File testClass = new File( compileMojo.getOutputDirectory(), "compiled.class" );
        assertTrue( testClass.exists() );

        configureMojo( compileMojo, testCompileMojo );

        setVariableValueToObject( testCompileMojo, "compilerManager", new CompilerManagerStub() );
        setVariableValueToObject( testCompileMojo, "testIncludes", includes );
        setVariableValueToObject( testCompileMojo, "testExcludes", excludes );

        testCompileMojo.execute();

        testClass = new File( testCompileMojo.getOutputDirectory(), "compiled.class" );
        assertTrue( testClass.exists() );
    }

    @Test
    @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-fail-test/plugin-config.xml"  )
    public void testCompileFailure( CompilerMojo compileMojo )
        throws Exception
    {
        configureMojo( compileMojo );

        setVariableValueToObject( compileMojo, "compilerManager", new CompilerManagerStub( true ) );

        assertThrows( CompilationFailureException.class, compileMojo::execute, "Should throw an exception" );
    }

    @Test
    @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-failonerror-test/plugin-config.xml"  )
    public void testCompileFailOnError( CompilerMojo compileMojo )
        throws Exception
    {
        configureMojo( compileMojo );

        setVariableValueToObject( compileMojo, "compilerManager", new CompilerManagerStub( true ) );

        assertDoesNotThrow( compileMojo::execute, "The compilation error should have been consumed because failOnError = false" );
    }
    
    /**
     * Tests that setting 'skipMain' to true skips compilation of the main Java source files, but that test Java source
     * files are still compiled.
     */
    @Test
    public void testCompileSkipMain(
            @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-skip-main/plugin-config.xml"  )
            CompilerMojo compileMojo,

            @InjectMojo( goal = "testCompile", pom = "classpath:/unit/compiler-skip-main/plugin-config.xml"  )
            TestCompilerMojo testCompileMojo
    )
            throws Exception
    {
        configureMojo( compileMojo );
        setVariableValueToObject( compileMojo, "skipMain", true );
        compileMojo.execute();
        File testClass = new File( compileMojo.getOutputDirectory(), "TestSkipMainCompile0.class" );
        assertFalse( testClass.exists() );

        configureMojo( compileMojo, testCompileMojo );
        testCompileMojo.execute();
        testClass = new File( testCompileMojo.getOutputDirectory(), "TestSkipMainCompile0Test.class" );
        assertTrue( testClass.exists() );
    }

    /**
     * Tests that setting 'skip' to true skips compilation of the test Java source files, but that main Java source
     * files are still compiled.
     */
    @Test
    public void testCompileSkipTest(
            @InjectMojo( goal = "compile", pom = "classpath:/unit/compiler-skip-test/plugin-config.xml"  )
            CompilerMojo compileMojo,

            @InjectMojo( goal = "testCompile", pom = "classpath:/unit/compiler-skip-test/plugin-config.xml"  )
            TestCompilerMojo testCompileMojo
    )
        throws Exception
    {
        configureMojo( compileMojo );
        compileMojo.execute();
        File testClass = new File( compileMojo.getOutputDirectory(), "TestSkipTestCompile0.class" );
        assertTrue( testClass.exists() );

        configureMojo( compileMojo, testCompileMojo );
        setVariableValueToObject( testCompileMojo, "skip", true );
        testCompileMojo.execute();
        testClass = new File( testCompileMojo.getOutputDirectory(), "TestSkipTestCompile0Test.class" );
        assertFalse( testClass.exists() );
    }

    private CompilerMojo configureMojo( CompilerMojo mojo )
        throws Exception
    {
//        setVariableValueToObject( mojo, "log", new DebugEnabledLog() );
        setVariableValueToObject( mojo, "projectArtifact", new ArtifactStub() );
        setVariableValueToObject( mojo, "compilePath", Collections.EMPTY_LIST );
        setVariableValueToObject( mojo, "session", session );
        setVariableValueToObject( mojo, "project", getMockProject() );
        setVariableValueToObject( mojo, "mojoExecution", getMockMojoExecution() );
        setVariableValueToObject( mojo, "source", source );
        setVariableValueToObject( mojo, "target", target );

        return mojo;
    }

    private TestCompilerMojo configureMojo( CompilerMojo compilerMojo, TestCompilerMojo mojo )
        throws Exception
    {
//        File testPom = new File( getBasedir(), pomXml );

//        setVariableValueToObject( mojo, "log", new DebugEnabledLog() );
//
//        File buildDir = (File) getVariableValueFromObject( compilerMojo, "buildDirectory" );
//        File testClassesDir = new File( buildDir, "test-classes" );
//        setVariableValueToObject( mojo, "outputDirectory", testClassesDir );
//
        List<String> testClasspathList = new ArrayList<>();
//
//        Artifact ll junitArtifact = mock( Artifact.class );
//        ArtifactHandler handler = mock( ArtifactHandler.class );
//        when( handler.isAddedToClasspath() ).thenReturn( true );
//        when( junitArtifact.getArtifactHandler() ).thenReturn( handler );
//
        File artifactFile;
        String localRepository = System.getProperty( "localRepository" );
        if ( localRepository != null )
        {
            artifactFile = new File( localRepository, "junit/junit/3.8.1/junit-3.8.1.jar" );
        }
        else
        {
//             for IDE
            String junitURI = org.junit.Test.class.getResource( "Test.class" ).toURI().toString();
            junitURI = junitURI.substring( "jar:".length(), junitURI.indexOf( '!' ) );
            artifactFile = new File( URI.create( junitURI ) );
        }
//        when ( junitArtifact.getFile() ).thenReturn( artifactFile );
        
        testClasspathList.add( artifactFile.getAbsolutePath() );
        testClasspathList.add( compilerMojo.getOutputDirectory().getPath() );

        String testSourceRoot = testPom.getParent() + "/src/test/java";
        setVariableValueToObject( mojo, "compileSourceRoots", Collections.singletonList( testSourceRoot ) );

        Project project = getMockProject();
//        project.setFile( testPom );
//        project.addCompileSourceRoot("/src/main/java" );
//        project.setArtifacts( Collections.singleton( junitArtifact )  );
//        project.getBuild().setOutputDirectory( new File( buildDir, "classes" ).getAbsolutePath() );
        setVariableValueToObject( mojo, "project",  getMockProject() );
        setVariableValueToObject( mojo, "testPath", testClasspathList );
        setVariableValueToObject( mojo, "session", session );
        setVariableValueToObject( mojo, "mojoExecution", getMockMojoExecution() );
        setVariableValueToObject( mojo, "source", source );
        setVariableValueToObject( mojo, "target", target );
        return mojo;
    }

    @Provides
    private Project getMockProject()
    {
        Model model = new Model();
        model.setGroupId( "unknown" );
        model.setArtifactId( "empty-project" );
        model.setVersion( "0" );
        model.setBuild( new Build() );
        model.getBuild().setDirectory( "target" );
        model.getBuild().setOutputDirectory( "target/classes" );
        model.getBuild().setSourceDirectory( "src/main/java" );
        model.getBuild().setTestOutputDirectory( "target/test-classes" );
        Project project = mock( Project.class );
        when( project.getModel() ).thenReturn( model );
        return project;
    }

    @Provides
    private Session getMockSession( ProjectManager projectManager )
    {
        Session session = mock( Session.class );
        // when( session.getPluginContext( isA(PluginDescriptor.class), isA(MavenProject.class) ) ).thenReturn(
        // Collections.emptyMap() );
//        when( session.getCurrentProject() ).thenReturn( getMockMavenProject() );

        when( session.getService( ProjectManager.class ) ).thenReturn( projectManager );
        return session;
    }

    @Provides
    private ProjectManager getMockProjectManager()
    {
        ProjectManager projectManager = mock( ProjectManager.class );
        return projectManager;
    }

    @Provides
    private ArtifactManager getMockArtifactManager()
    {
        ArtifactManager artifactManager = mock( ArtifactManager.class );
        return artifactManager;
    }

    @Provides
    private ToolchainManager getMockToolchainManager()
    {
        ToolchainManager toolchainManager = mock( ToolchainManager.class );
        return toolchainManager;
    }

    @Provides
    private MojoExecution getMockMojoExecution()
    {
//        MojoDescriptor md = new MojoDescriptor();
//        md.setGoal( "compile" );
//
//        PluginDescriptor pd = new PluginDescriptor();
//        pd.setArtifactId( "maven-compiler-plugin" );
//        md.setPluginDescriptor( pd );

        Plugin plugin = new Plugin();
        plugin.setArtifactId( "maven-compiler-plugin" );

        MojoExecution me = mock( MojoExecution.class );
        when( me.getPlugin() ).thenReturn( plugin );
        when( me.getGoal() ).thenReturn( "compile" );
        return me;
    }
}
