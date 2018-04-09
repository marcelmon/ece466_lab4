#
# A simple makefile for compiling three java classes
#

# define a makefile variable for the java compiler
#
JCC = javac

# define a makefile variable for compilation flags
# the -g flag compiles with debugging information
#
JFLAGS = -g 

# -Xlint:unchecked

# typing 'make' will invoke the first target entry in the makefile 
# (the default one in this case)
#
default: \
	Estimator.class
	


# MORE DEFAULT Bucket.class Buffer.class TokenBucket.class TokenBucketReceiver.class TokenBucketSender.class

# this target entry builds the Average class
# the Average.class file is dependent on the Average.java file
# and the rule associated with this entry gives the command to create it
#
Estimator.class: Estimator.java
		$(JCC) $(JFLAGS) Estimator.java


# To start over from scratch, type 'make clean'.  
# Removes all .class files, so that the next make rebuilds them
#
clean: 
		$(RM) *.class