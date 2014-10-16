ifeq ($(origin JAVA_HOME), undefined)
  JAVA_HOME=/usr
endif

ifneq (,$(findstring CYGWIN,$(shell uname -s)))
  COLON=\;
  JAVA_HOME := `cygpath -up "$(JAVA_HOME)"`
else
  COLON=:
endif

SRCS=$(wildcard src/*.java)

matrix.jar matrix.jar.pack.gz: $(SRCS) Jama-1.0.3.jar Jama-1.0.3.jar.pack.gz NetLogoHeadless.jar scala-library-2.10.4.jar  Makefile manifest.txt
	mkdir -p classes
	$(JAVA_HOME)/bin/javac -g -encoding us-ascii -source 1.7 -target 1.7 -classpath NetLogoHeadless.jar$(COLON)scala-library-2.10.4.jar$(COLON)Jama-1.0.3.jar -d classes $(SRCS)
	jar cmf manifest.txt matrix.jar -C classes .
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip matrix.jar.pack.gz matrix.jar

NetLogoHeadless.jar:
	curl -f -s -S 'http://ccl.northwestern.edu/devel/6.0-M1/NetLogoHeadless.jar' -o NetLogoHeadless.jar

scala-library-2.10.4.jar:
	curl -f -s -S 'http://ccl.northwestern.edu/devel/scala-library-2.10.4.jar' -o scala-library-2.10.4.jar

Jama-1.0.3.jar Jama-1.0.3.jar.pack.gz:
	curl -f -s -S 'http://math.nist.gov/javanumerics/jama/Jama-1.0.3.jar' -o Jama-1.0.3.jar
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip Jama-1.0.3.jar.pack.gz Jama-1.0.3.jar

matrix.zip: matrix.jar
	rm -rf matrix
	mkdir matrix
	cp -rp matrix.jar matrix.jar.pack.gz Jama-1.0.3.jar Jama-1.0.3.jar.pack.gz README.md Makefile src manifest.txt matrix
	zip -rv matrix.zip matrix
	rm -rf matrix

## support for running tests.txt (via `make test`)

lib/scalatest_2.10.4-1.8.jar:
	mkdir -p lib
	(cd lib; curl -O -f -s -S 'http://search.maven.org/remotecontent?filepath=org/scalatest/scalatest_2.10.4/1.8/scalatest_2.10.4-1.8.jar')
lib/scala-library.jar:
	mkdir -p lib
	(cd lib; curl -O -f -s -S 'http://ccl.northwestern.edu/netlogo/5.0.5/lib/scala-library.jar')
lib/picocontainer-2.13.6.jar:
	mkdir -p lib
	(cd lib; curl -O -f -s -S 'http://ccl.northwestern.edu/netlogo/5.0.5/lib/picocontainer-2.13.6.jar')
lib/asm-all-3.3.1.jar:
	mkdir -p lib
	(cd lib; curl -O -f -s -S 'http://ccl.northwestern.edu/netlogo/5.0.5/lib/asm-all-3.3.1.jar')

.PHONY: test
test: matrix.jar NetLogo.jar Jama-1.0.2.jar tests.txt lib/NetLogo-tests.jar lib/scalatest_2.10.4-1.8.jar lib/scala-library.jar lib/picocontainer-2.13.6.jar lib/asm-all-3.3.1.jar
	rm -rf tmp; mkdir tmp
	mkdir -p tmp/extensions/matrix
	cp matrix.jar Jama*.jar tests.txt tmp/extensions/matrix
	(cd tmp; ln -s ../lib)
	(cd tmp; $(JAVA_HOME)/bin/java \
	  -classpath ../NetLogo.jar:../lib/scalatest_2.10.4-1.8.jar \
	  -Djava.awt.headless=true \
	  org.scalatest.tools.Runner -o \
	  -R ../lib/NetLogo-tests.jar \
	  -s org.nlogo.headless.TestExtensions)
