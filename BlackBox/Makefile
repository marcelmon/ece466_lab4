#
# A simple makefile for compiling three java classes
#

# define a makefile variable for the java compiler
#
JCC = javac

# define a makefile variable for compilation flags
# the -g flag compiles with debugging information
#
JFLAGS = -g -d .

# -Xlint:unchecked

# typing 'make' will invoke the first target entry in the makefile 
# (the default one in this case)
#
default: Buffer.class Bucket.class TokenBucketSender.class TokenBucketReceiver.class BlackBox.class
	


# MORE DEFAULT Bucket.class Buffer.class TokenBucket.class TokenBucketReceiver.class TokenBucketSender.class

# this target entry builds the Average class
# the Average.class file is dependent on the Average.java file
# and the rule associated with this entry gives the command to create it
#
Buffer.class: Buffer.java
		$(JCC) $(JFLAGS) Buffer.java

Bucket.class: Bucket.java
		$(JCC) $(JFLAGS) Bucket.java

TokenBucketSender.class: TokenBucketSender.java
		$(JCC) $(JFLAGS) TokenBucketSender.java

TokenBucketReceiver.class: TokenBucketReceiver.java
		$(JCC) $(JFLAGS) TokenBucketReceiver.java





BlackBox.class: BlackBox.java
		$(JCC) $(JFLAGS) BlackBox.java



# To start over from scratch, type 'make clean'.  
# Removes all .class files, so that the next make rebuilds them
#
clean: 
		$(RM) *.class