# Spark Demo

## What is this for?
This repo intends to give some examples that help explain how object serialization works in Scala and Spark,
and in particular why you may come across serialization errors when writing Spark code.

## Why is serialization important?
When Java/Scala objects are sent across the network they first need to be Serialized. With Spark's
distributed computing model this serialization happens a lot, as it is fundamentally about distributed
computing.

## When do problems occur?
When Spark has to send some object to another worker but it isn't possible to serialize the object you
will get a runtime error - yuck! There are 2 main strategies for dealing with this:
1) Make the relevant objects serializable by extending `Serializable`
2) Don't serialize objects unless you absolutely have to, reducing the objects that need to be Serializable

## Motivating examples
The [SparkSerializationSpec](src/test/scala/sparky/SparkSerializationSpec.scala) contains some examples -
some will fail and some will run without issue.
Before running them try to identify which ones will run without issue and which will throw exceptions.
Where they throw exceptions try to understand why and fix them, you'll learn much more by experimenting!

## Explanations
[Explanations.md](Explanations.md) contains explanations for why each of the test cases succeeds or fails.