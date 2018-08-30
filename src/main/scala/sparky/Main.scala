package sparky

class Main extends App {
  override def main(args: Array[String]): Unit = {

  }
}

//object Example {
//  val one = 1
//
//  class WithSparkMap(reduceInts: Int => Int) {
//    def myFunc = {
//      val reduceIntsEnc = reduceInts
//      testRdd
//        .map { e =>
//          reduceIntsEnc(e)
//        }
//        .collect
//        .toList shouldBe List(2, 3, 4)
//    }
//  }
//
//  def run = {
//    val addOne = (num: Int) => num + one
//    val withSparkMap = new WithSparkMap(num => addOne(num))
//    withSparkMap.myFunc
//  }
//}
