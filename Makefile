ifeq ($(origin JAVA_HOME), undefined)
  JAVA_HOME=/usr
endif

ifeq ($(origin NETLOGO), undefined)
  NETLOGO=../..
endif

SRCS=$(wildcard src/*.java)

matrix.jar: $(SRCS) Jama-1.0.2.jar manifest.txt
	mkdir -p classes
	$(JAVA_HOME)/bin/javac -g -encoding us-ascii -source 1.5 -target 1.5 -classpath $(NETLOGO)/NetLogoLite.jar:Jama-1.0.2.jar -d classes $(SRCS)
	jar cmf manifest.txt matrix.jar -C classes .

Jama-1.0.2.jar:
	curl -f -S 'http://ccl.northwestern.edu/devel/Jama-1.0.2.jar' -o Jama-1.0.2.jar

matrix.zip: matrix.jar
	rm -rf matrix
	mkdir matrix
	cp -rp matrix.jar Jama-1.0.2.jar README.md Makefile src manifest.txt matrix
	zip -rv matrix.zip matrix
	rm -rf matrix
