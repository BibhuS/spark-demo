# Explanations
Here you will find explanations for why each of the examples passes or fails. One fundamental point worth being aware of
upfront is that only objects can be serialized. For example if you try to serialize a method in an object it will have
to serialize the whole of the containing object. This can easily lead to far more being serialized than you had
intended!

## Example 1 - basic spark map
```
object Example {
    def myFunc = testRdd.map(_ + 1).collect.toList shouldBe List(2, 3, 4)
}
```
**PASSES**  
Any function within a "map" or similar function on an RDD will be serialized and sent to the Spark worker nodes.
In this case the function is an anonymous function, which is an instance of the serializable Function1 class.

## Example 2 - spark map with external variable e.g.1
```
object Example {
    val num = 1
    def myFunc = testRdd.map(_ + num).collect.toList shouldBe List(2, 3, 4)
}
```
**FAILS**  
In this case the anonymous function refers to a value in a different scope. In this case the value being referred to
sits in the Example object. Where a value in an object is referenced it will try to serialize the whole
object - in this case the Example object! The Example object doesn't extend Serializable, so the serialization
will fail.

## Example 3 - spark map with external variable e.g.2.
object Example extends Serializable {
    val num = 1
    def myFunc = testRdd.map(_ + num).collect.toList shouldBe List(2, 3, 4)
}
**PASSES**  
Rationale as per example 2, except in this case we have made the Example object serializable. But wouldn't it be nicer
if we didn't have to serialize the whole object...

## Example 4 - spark map with external variable e.g.3.
```
object Example {
    val num = 1
    def myFunc = {
      lazy val enclosedNum = num
      testRdd.map(_ + enclosedNum).collect.toList shouldBe List(2, 3, 4)
    }
}
```
**FAILS**  
In this case we create an enclosedNum value inside the scope of myFunc - when this is referenced it should stop trying
to serialize the whole object because it can access everything required in a smaller scope (lines 40 to 43). However
because enclosedNum is a lazy val this still won't work, as it still requires knowledge of num and hence will still
try to serialize the whole of the Example object.

## Example 5 - spark map with external variable e.g.4.
```
object Example {
    val num = 1
    def myFunc = {
      val enclosedNum = num
      testRdd.map(_ + enclosedNum).collect.toList shouldBe List(2, 3, 4)
    }
}
```
**PASSES**  
Similar to the previous example, but this time with enclosedNum being a val, which fixes the previous issue.

## Example 6 - nested object e.g.1
```
object Example {
    val outerNum = 1
    object NestedExample extends Serializable {
      val innerNum = 10
      def myFunc =
        testRdd.map(_ + innerNum).collect.toList shouldBe List(11, 12, 13)
    }
}
```
**PASSES**
A slightly more complex example but with the same principles. Here innerNum is being referenced by the map function.
This triggers serialization of the whole of the NestedExample object. However this is fine because it extends
Serializable. You could use the same enclosing trick as before to stop the serialization of the NestedExample object too.

## Example 7 - nested object e.g.2
```
object Example {
    val outerNum = 1
    object NestedExample extends Serializable {
      val innerNum = 10
      def myFunc =
        testRdd.map(_ + outerNum).collect.toList shouldBe List(2, 3, 4)
    }
}
```
**FAILS**  
In this case outerNum is being referenced inside the map function. This means the whole Example object would have to
be serialized, which will fail as it isn't Serializable.

## Example 8 - nested object e.g.3
```
object Example {
    val outerNum = 1
    object NestedExample extends Serializable {
      val innerNum = 10
      val encOuterNum = outerNum
      def myFunc =
        testRdd.map(_ + encOuterNum).collect.toList shouldBe List(2, 3, 4)
    }
}
```
**PASSES**  
In this example we have fixed the previous issue by providing encOuterNum. Now the map references only values in the
NestedExample object, which can be serialized.

## Example 9 - adding some complexity e.g.1 - base example
```
      object Example {

        class WithFunction(val num: Int) {
          def plusOne(num2: Int) = num2 + num
        }

        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc =
            testRdd.map(reduceInts).collect.toList shouldBe List(2, 3, 4)
        }

        def run = {
          val withFunction = new WithFunction(1)
          val withSparkMap = new WithSparkMap(withFunction.plusOne)
          withSparkMap.myFunc
        }
      }
```
**FAILS**  
Now for some practice! This example is relatively complex and needs a few changes to work successfully. Can you figure
out what they are? Kudos is so! The next few examples walk through a solution step by step, and some things you may try.

## Example 10 - adding some complexity e.g.2 - make classes serializable
```
      object Example {

        class WithFunction(val num: Int) extends Serializable {
          def plusOne(num2: Int) = num2 + num
        }

        class WithSparkMap(reduceInts: Int => Int) extends Serializable {
          def myFunc =
            testRdd.map(reduceInts).collect.toList shouldBe List(2, 3, 4)
        }

        def run = {
          val withFunction = new WithFunction(1)
          val withSparkMap = new WithSparkMap(withFunction.plusOne)
          withSparkMap.myFunc
        }
      }
```
**FAILS**  
One approach to serialization issues can be to make everything Serializable. However in this case you will find it
doesn't solve the issue.

## Example 11a - adding some complexity e.g.3a - use anon function
```
      object Example {
        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            testRdd
              .map { e =>
                reduceInts(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val withSparkMap = new WithSparkMap(num => num + 1)
          withSparkMap.myFunc
        }
      }
```
**FAILS**
In order to debug this you might try simplifying things by replacing the WithFunction class with a simple anonymous 
function. However in this case we still have an a failure, can you spot the issue now?

## Example 11b - adding some complexity e.g.3b - use anon function, with enclosing
```
      object Example {
        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val withSparkMap = new WithSparkMap(num => num + 1)
          withSparkMap.myFunc
        }
      }
```
**PASSES**
Did you spot it? By enclosing the reduceInts method the map function can now access everything it needs in
that one closure, no need to serialize the other classes!

## Example 12a - adding some complexity e.g.4a - use function with def
Ok great, but really we don't want an anonymous function there. Let's take one more step and try to extract that:
```
      object Example {
        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          def addOne(num: Int) = num + 1
          val withSparkMap = new WithSparkMap(num => addOne(num))
          withSparkMap.myFunc
        }
      }
```
**FAILS**  
Again you will find this fails, but seeing why isn't easy. It is because of the intricacies of how `def` works.
Essentially a method defined with def contains an implicit reference to `this`, which in this case is an object
which can't be serialized.

## Example 12b - adding some complexity e.g.4b - use function with val
```
      object Example {
        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val addOne = (num: Int) => num + 1
          val withSparkMap = new WithSparkMap(num => addOne(num))
          withSparkMap.myFunc
        }
      }
```
**PASSES**   
Declaring the method with `val` works. A `val` method equates to a Function1 object, which is serializable, 
and doesn't contain an implicit reference to `this`, stopping the attempted serialization of the Example object.
There's a [good explanation of what is happening under the hood here](https://alvinalexander.com/scala/fp-book-diffs-val-def-scala-functions)

## Example 12c - adding some complexity e.g.4c - use function with val explained part 1
```
      object Example {
        val one = 1

        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val addOne = (num: Int) => num + one
          val withSparkMap = new WithSparkMap(num => addOne(num))
          withSparkMap.myFunc
        }
      }
```
**FAILS**  
This example serves to illustrate the point more clearly. Here the `addOne` function references the `one` value.
As we saw earlier this will cause the whole `Example` object to be serialized, which will fail.

**BONUS POINTS**  
One helpful experiment to try here is to resolve this by making the `Example` object serializable.
You will note that you still get a serialization error. Can you see why? There are actually 2 reasons:
1) `testRdd` is referenced inside the Example object, leading to the whole Spec trying to be serialized
2) The `shouldBe` method is also referenced, again leading to the whole Spec trying to be serialized 

## Example 12d - adding some complexity e.g.4d - use function with val explained part 2
```
      object Example {
        val one = 1

        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val oneEnc = one
          val addOne = (num: Int) => num + oneEnc
          val withSparkMap = new WithSparkMap(num => addOne(num))
          withSparkMap.myFunc
        }
      }
```
**PASSES**  
As above, the best way to fix the issue is to reference values only in the more immediate scope.
Here we have added oneEnc, which prevents the serialization of the whole Example object.

## Example 13 - adding some complexity e.g.5 - back to the problem, no class params
```
      object Example {
        class WithFunction {
          val plusOne = (num2: Int) => num2 + 1
        }

        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val withSparkMap = new WithSparkMap((new WithFunction).plusOne)
          withSparkMap.myFunc
        }
      }
```
**PASSES**  
Coming back from the issue we originally had, now we understand a little more let's introduce
our WithFunction class back in. To simplify things we've taken out the constructor parameter here.
We're also using a val for the method rather than a def. No serialization issues now!

## Example 14 - adding some complexity e.g.6 - back to the problem, with class params
Let's now add our class params back in
```
      object Example {
        class WithFunction(val num: Int) {
          val plusOne = (num2: Int) => num2 + num
        }

        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val withSparkMap = new WithSparkMap(new WithFunction(1).plusOne)
          withSparkMap.myFunc
        }
      }
```
**FAILS**
Can you spot why this fails? The plusOne function references num, outside of the immediate scope,
again causing more objects to be serialized which is failing.

## Example 15a - adding some complexity e.g.7a - back to the problem, with class params, and enclosing
```
      object Example {
        class WithFunction(val num: Int) extends Serializable {
          val plusOne = {
            val encNum = num
            num2: Int => num2 + encNum
          }
        }

        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val withSparkMap = new WithSparkMap(new WithFunction(1).plusOne)
          withSparkMap.myFunc
        }
      }

```
**PASSES**
This is now a simple fix, and we can enclose the `num` value with `encNum` which resolves the last of our
serialization issues.

## Example 15b - adding some complexity e.g.7b - back to the problem, with class params, and other enclosing
```
      object Example {
        class WithFunction(val num: Int) {
          val plusOne = { num2: Int =>
            {
              val encNum = num
              num2 + encNum
            }
          }
        }

        class WithSparkMap(reduceInts: Int => Int) {
          def myFunc = {
            val reduceIntsEnc = reduceInts
            testRdd
              .map { e =>
                reduceIntsEnc(e)
              }
              .collect
              .toList shouldBe List(2, 3, 4)
          }
        }

        def run = {
          val withSparkMap = new WithSparkMap(new WithFunction(1).plusOne)
          withSparkMap.myFunc
        }
      }
```
**FAILS**  
Can you see why the above fails? The issue is that `encNum` won't be evaluated until `plusOne` is actually
called, effectively within the map function. At this point then the `num` value will need to be accessed, causing
additional serialization of the containing object and the failure here.
