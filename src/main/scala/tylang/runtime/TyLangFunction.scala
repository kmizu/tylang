package tylang.runtime

// TyLang function interfaces
trait TyLangFunction0[R] {
  def apply(): R
}

trait TyLangFunction1[T, R] {
  def apply(arg: T): R
}

trait TyLangFunction2[T1, T2, R] {
  def apply(arg1: T1, arg2: T2): R
}

// Specialized interfaces for primitive types to avoid boxing
trait IntToIntFunction {
  def apply(x: Int): Int
}

trait IntIntToIntFunction {
  def apply(x: Int, y: Int): Int
}

trait IntToDoubleFunction {
  def apply(x: Int): Double
}

trait IntToBooleanFunction {
  def apply(x: Int): Boolean
}

trait IntToStringFunction {
  def apply(x: Int): String
}

// Helper object for function utilities
object TyLangFunction {
  // Convert method references to function objects
  def methodRef0[R](method: () => R): TyLangFunction0[R] = new TyLangFunction0[R] {
    def apply(): R = method()
  }
  
  def methodRef1[T, R](method: T => R): TyLangFunction1[T, R] = new TyLangFunction1[T, R] {
    def apply(arg: T): R = method(arg)
  }
  
  def methodRef2[T1, T2, R](method: (T1, T2) => R): TyLangFunction2[T1, T2, R] = new TyLangFunction2[T1, T2, R] {
    def apply(arg1: T1, arg2: T2): R = method(arg1, arg2)
  }
}