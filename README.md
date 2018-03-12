Jasper compiler maven plugin
=============

This maven plugin compiles Jasper files to the target directory.

Motivation
----------
The original (https://github.com/alexnederlof/Jasper-report-maven-plugin) plugin got outdated. The goal was to contribute to the original repository, but while wanted to bump
the dependencies version the whole thing got unusable. The maven mojo and the compiler task are same as the original, but I made the Jasperreports dependency provided as
the user shall include the desired version of the jasper compiler for compatibility.


Usage
-----

The plugin is provided as is and not planning to release to the central repository.
Check out & run Maven clean install.

An example project can be found here:
https://github.com/Szil/jasper-compiler-sample

You can use the plugin by adding it to the plug-in section in your pom;

```xml
<build>
	<plugins>
		<plugin>
			<groupId>com.github.szil</groupId>
			<artifactId>jasper-compiler-maven-plugin</artifactId>
			<version>3.0.0-SNAPSHOT</version>
			<executions>
				<execution>
					<phase>process-sources</phase>
	   				<goals>
	      				<goal>jasper</goal>
	   				</goals>
	   			</execution>
			</executions>
			<configuration>
				<!-- These are the default configurations: -->
				<compiler>net.sf.jasperreports.engine.design.JRJdtCompiler</compiler>
				<sourceDirectory>src/main/jasperreports</sourceDirectory>
				<outputDirectory>${project.build.directory}/jasper</outputDirectory>
				<outputFileExt>.jasper</outputFileExt>
				<xmlValidation>true</xmlValidation>
				<verbose>false</verbose>
				<numberOfThreads>4</numberOfThreads>
				<failOnMissingSourceDirectory>true</failOnMissingSourceDirectory>
				<sourceScanner>org.codehaus.plexus.compiler.util.scan.StaleSourceScanner</sourceScanner>
			</configuration>
		</plugin>
	</plugins>
</build>
```

If you want to pass any Jasper options to the compiler you can do so by adding them to the configuration like so:

```xml
<plugin>
	...
	<configuration>
		...
		<additionalProperties>
			<net.sf.jasperreports.awt.ignore.missing.font>true</net.sf.jasperreports.awt.ignore.missing.font>
			<net.sf.jasperreports.default.pdf.font.name>Courier</net.sf.jasperreports.default.pdf.font.name>
			<net.sf.jasperreports.default.pdf.encoding>UTF-8</net.sf.jasperreports.default.pdf.encoding>
			<net.sf.jasperreports.default.pdf.embedded>true</net.sf.jasperreports.default.pdf.embedded>
           </additionalProperties>
	</configuration>
</plugin>
```

You can also add extra elements to the classpath using

```xml
<plugin>
	...
	<configuration>
		...
		<classpathElements>
			<element>your.classpath.element</element>
        </classpathElements>
	</configuration>
</plugin>
```
