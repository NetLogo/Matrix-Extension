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

matrix.jar matrix.jar.pack.gz: $(SRCS) Jama-1.0.3.jar Jama-1.0.3.jar.pack.gz NetLogoLite.jar Makefile manifest.txt
	mkdir -p classes
	$(JAVA_HOME)/bin/javac -g -encoding us-ascii -source 1.5 -target 1.5 -classpath NetLogoLite.jar$(COLON)Jama-1.0.3.jar -d classes $(SRCS)
	jar cmf manifest.txt matrix.jar -C classes .
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip matrix.jar.pack.gz matrix.jar

NetLogoLite.jar:
	curl -f -s -S 'http://ccl.northwestern.edu/netlogo/5.0.5/NetLogoLite.jar' -o NetLogoLite.jar

Jama-1.0.3.jar Jama-1.0.3.jar.pack.gz:
	curl -f -s -S 'http://math.nist.gov/javanumerics/jama/Jama-1.0.3.jar' -o Jama-1.0.3.jar
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip Jama-1.0.3.jar.pack.gz Jama-1.0.3.jar

matrix.zip: matrix.jar
	rm -rf matrix
	mkdir matrix
	cp -rp matrix.jar matrix.jar.pack.gz Jama-1.0.3.jar Jama-1.0.3.jar.pack.gz README.md Makefile src manifest.txt matrix
	zip -rv matrix.zip matrix
	rm -rf matrix
