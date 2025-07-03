# TyLang Language Reference

TyLangは静的型付けと構造的サブタイピングを特徴とする、JVMターゲットのプログラミング言語です。

## 目次

1. [言語概要](#言語概要)
2. [基本構文](#基本構文)
3. [型システム](#型システム)
4. [データ型](#データ型)
5. [関数](#関数)
6. [クラスとオブジェクト](#クラスとオブジェクト)
7. [拡張メソッド](#拡張メソッド)
8. [制御構造](#制御構造)
9. [コメント](#コメント)
10. [演算子](#演算子)
11. [REPL](#repl)
12. [例題集](#例題集)

## 言語概要

### 設計原則

- **静的型付け**: コンパイル時の型安全性
- **構造的サブタイピング**: 名前ではなく構造による型の互換性
- **拡張メソッド中心**: すべてのメソッドは拡張メソッドとして実装
- **式ベース構文**: すべてが値を返す式
- **JVM互換性**: Javaライブラリとのシームレスな統合

### 特徴

- コンパイル時型チェック
- 型推論（ローカル変数・ラムダパラメーター）
- ジェネリクス（宣言サイト分散サポート）
- 構造的型マッチング
- インタラクティブREPL

## 基本構文

### プログラム構造

```tylang
// 関数定義
fun greet(name: String): String {
  "Hello, " + name + "!"
}

// クラス定義
class Person(name: String, age: Int) {
  fun getName(): String { name }
  fun getAge(): Int { age }
}

// オブジェクト定義（シングルトン）
object Constants {
  fun pi(): Double { 3.14159 }
  fun version(): String { "1.0" }
}
```

### 変数宣言

```tylang
// 不変変数
val name = "TyLang"
val version: String = "1.0"

// 可変変数
var counter = 0
var status: Boolean = true
```

## 型システム

### 基本型

| 型 | 説明 | 例 |
|---|---|---|
| `Int` | 32ビット整数 | `42`, `-123` |
| `Double` | 64ビット浮動小数点 | `3.14`, `-0.5` |
| `String` | 文字列 | `"Hello"`, `"World"` |
| `Boolean` | 真偽値 | `true`, `false` |
| `Unit` | 戻り値なし | `()` |

### 関数型

```tylang
// 1引数の関数型は括弧を省略可能
val increment: Int => Int = (x) => x + 1
val toUpperCase: String => String = (s) => s.toUpperCase()

// 複数引数の場合は括弧が必要
val add: (Int, Int) => Int = (x, y) => x + y
val format: (String, Int, Boolean) => String = (s, n, b) => s + n + b

// 高階関数の型
val curry: (Int, Int) => Int => Int = (x, y) => (z) => x + y + z
val transform: Int => String => Boolean = (x) => (s) => s.length() > x
```

### 構造的サブタイピング

型の互換性は名前ではなく構造で決まります：

```tylang
// 構造的型定義
val drawable: { fun draw(): Unit } = someObject

// someObjectが draw(): Unit メソッドを持てば互換
class Circle {
  fun draw(): Unit { /* 描画処理 */ }
}

class Rectangle {
  fun draw(): Unit { /* 描画処理 */ }
}

// CircleもRectangleもdrawableに代入可能
```

### ジェネリクス

```tylang
// 不変（デフォルト）
class Box<T>(value: T) {
  fun get(): T { value }
}

// 共変（covariant）
class Producer<+T>(value: T) {
  fun produce(): T { value }
}

// 反変（contravariant）
class Consumer<-T> {
  fun consume(value: T): Unit { /* 処理 */ }
}
```

### 型推論

```tylang
// 型推論が働く例
val number = 42              // Int型と推論
val message = "Hello"        // String型と推論
val result = 3.14 * 2        // Double型と推論

// ラムダの型推論
val square = (x: Int) => x * x  // (Int) => Int と推論
```

## データ型

### リスト

```tylang
// リスト作成
val numbers = [1, 2, 3, 4, 5]
val names = ["Alice", "Bob", "Charlie"]

// 型注釈付き
val scores: List<Int> = [85, 92, 78]
```

### マップ

```tylang
// マップ作成
val ages = ["Alice": 25, "Bob": 30, "Charlie": 35]

// 型注釈付き
val config: Map<String, String> = ["host": "localhost", "port": "8080"]
```

## 関数

### 基本的な関数定義

```tylang
// 単純な関数
fun add(x: Int, y: Int): Int {
  x + y
}

// 戻り値型推論
fun multiply(x: Int, y: Int) {
  x * y  // Int型と推論される
}

// 再帰関数
fun factorial(n: Int): Int {
  if (n <= 1) { 1 } else { n * factorial(n - 1) }
}
```

### 高階関数

```tylang
// 関数を引数に取る (1引数の場合は括弧を省略可能)
fun applyTwice(f: Int => Int, x: Int): Int {
  f(f(x))
}

// 複数引数の場合は括弧が必要
fun combine(f: (Int, Int) => Int, x: Int, y: Int): Int {
  f(x, y)
}

// 使用例
fun double(x: Int): Int { x * 2 }
val result = applyTwice(double, 5)  // 20
```

### ラムダ式

```tylang
// ラムダ式の定義
val increment = (x: Int) => x + 1
val sum = (x: Int, y: Int) => x + y

// 1引数の関数型は括弧を省略可能
val double: Int => Int = (x) => x * 2

// 複数引数の場合は括弧が必要
val add: (Int, Int) => Int = (x, y) => x + y

// 型推論を利用
val numbers = [1, 2, 3, 4, 5]
val doubled = numbers.map((x) => x * 2)
```

### Trailing Lambda構文

最後の引数がラムダ式の場合、括弧の外に記述できます：

```tylang
// 通常の構文
twice((x: Int) => x * 2, 3)
list.map((x) => x * 2)
fold(0, list)((acc, x) => acc + x)

// Trailing Lambda構文
twice{x => x * 2}(3)
list.map{x => x * 2}
fold(0, list){acc, x => acc + x}

// 単一パラメータの場合
filter{x => x > 0}

// 複数パラメータの場合（括弧なし）
reduce{a, b => a + b}

// パラメータに型注釈をつける場合
map{x: Int => x * 2}

// パラメータなしの場合
execute{ => println("Hello") }
```

## クラスとオブジェクト

### クラス定義

```tylang
// 基本的なクラス
class Point(x: Int, y: Int) {
  fun getX(): Int { x }
  fun getY(): Int { y }
  
  fun distance(): Double {
    Math.sqrt(x * x + y * y)
  }
  
  fun move(dx: Int, dy: Int): Point {
    Point(x + dx, y + dy)
  }
}
```

### 継承

```tylang
// 基底クラス
class Shape {
  fun area(): Double { 0.0 }
}

// 継承
class Rectangle(width: Double, height: Double) extends Shape {
  fun area(): Double { width * height }
}
```

### オブジェクト（シングルトン）

```tylang
object MathUtils {
  fun pi(): Double { 3.14159265359 }
  
  fun max(a: Int, b: Int): Int {
    if (a > b) { a } else { b }
  }
  
  fun fibonacci(n: Int): Int {
    if (n <= 1) { n } else { fibonacci(n-1) + fibonacci(n-2) }
  }
}

// 使用例
val pi = MathUtils.pi()
val maximum = MathUtils.max(10, 20)
```

### トレイト（インターフェース）

```tylang
trait Drawable {
  fun draw(): Unit
}

trait Movable {
  fun move(x: Int, y: Int): Unit
}

// 複数トレイトの実装
class Sprite extends Drawable with Movable {
  fun draw(): Unit { /* 描画処理 */ }
  fun move(x: Int, y: Int): Unit { /* 移動処理 */ }
}
```

## 拡張メソッド

TyLangでは既存の型に新しいメソッドを追加できます：

```tylang
// Int型の拡張
extension Int {
  fun isEven(): Boolean { this % 2 == 0 }
  fun isOdd(): Boolean { !this.isEven() }
  fun square(): Int { this * this }
  fun times(action: () => Unit): Unit {
    var i = 0
    while (i < this) {
      action()
      i = i + 1
    }
  }
}

// String型の拡張
extension String {
  fun reverse(): String {
    // 実装はネイティブメソッドを使用
    "reversed"  // 簡単な例
  }
  
  fun isPalindrome(): Boolean {
    this == this.reverse()
  }
}

// 使用例
val number = 42
if (number.isEven()) {
  println("偶数です")
}

val doubled = 5.square()  // 25

3.times {
  println("Hello!")  // 3回実行
}
```

## 制御構造

### 条件分岐

```tylang
// if-else式
val result = if (condition) {
  "真の場合"
} else {
  "偽の場合"
}

// else省略（Unit型を返す）
if (error) {
  handleError()
}

// ネストした条件
val grade = if (score >= 90) {
  "A"
} else if (score >= 80) {
  "B"
} else if (score >= 70) {
  "C"
} else {
  "F"
}
```

### ループ

```tylang
// while ループ
var i = 0
while (i < 10) {
  println(i)
  i = i + 1
}

// 条件付きループ
var running = true
while (running) {
  val input = readInput()
  if (input == "quit") {
    running = false
  }
}
```

### パターンマッチング（将来実装予定）

```tylang
// match式（計画中）
val result = value match {
  case 0 -> "zero"
  case 1 -> "one"
  case n if n > 0 -> "positive"
  case _ -> "negative"
}
```

## コメント

TyLangは3種類のコメントをサポートします：

```tylang
// 行コメント
val x = 42  // 行末コメント

/*
 * ブロックコメント
 * 複数行にわたる
 */

/**
 * Javadocスタイルコメント
 * ドキュメント生成用
 * @param name パラメーターの説明
 * @return 戻り値の説明
 */
fun greet(name: String): String {
  "Hello, " + name
}
```

## 演算子

### 算術演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `+` | 加算 | `5 + 3` → `8` |
| `-` | 減算 | `5 - 3` → `2` |
| `*` | 乗算 | `5 * 3` → `15` |
| `/` | 除算 | `6 / 3` → `2` |
| `%` | 剰余 | `7 % 3` → `1` |

### 比較演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `==` | 等価 | `5 == 5` → `true` |
| `!=` | 不等価 | `5 != 3` → `true` |
| `<` | 未満 | `3 < 5` → `true` |
| `>` | 超過 | `5 > 3` → `true` |
| `<=` | 以下 | `3 <= 5` → `true` |
| `>=` | 以上 | `5 >= 3` → `true` |

### 論理演算子

| 演算子 | 説明 | 例 |
|--------|------|-----|
| `&&` | 論理AND | `true && false` → `false` |
| `\|\|` | 論理OR | `true \|\| false` → `true` |
| `!` | 論理NOT | `!true` → `false` |

### 演算子優先順位

1. `!` (単項)
2. `*`, `/`, `%`
3. `+`, `-`
4. `<`, `>`, `<=`, `>=`
5. `==`, `!=`
6. `&&`
7. `||`

## REPL

TyLangは対話的なREPL（Read-Eval-Print Loop）を提供します：

### 起動

```bash
sbt run
```

### REPLコマンド

| コマンド | 説明 |
|----------|------|
| `:help`, `:h` | ヘルプを表示 |
| `:quit`, `:q` | REPLを終了 |
| `:reset` | セッションをリセット |
| `:list` | 定義済み関数・クラスを表示 |

### REPL使用例

```
tylang> 2 + 3 * 4
res1: 14

tylang> fun double(x: Int): Int { x * 2 }
Function 'double' defined.

tylang> double(21)
res2: 42

tylang> class Point(x: Int, y: Int) {
  fun sum(): Int { x + y }
}
Class 'Point' defined.

tylang> :list
Defined functions:
  double
Defined classes:
  Point
```

## 例題集

### 基本的な例

```tylang
// 1. フィボナッチ数列
fun fibonacci(n: Int): Int {
  if (n <= 1) { n } else { fibonacci(n-1) + fibonacci(n-2) }
}

// 2. 階乗計算
fun factorial(n: Int): Int {
  if (n <= 1) { 1 } else { n * factorial(n-1) }
}

// 3. 最大公約数
fun gcd(a: Int, b: Int): Int {
  if (b == 0) { a } else { gcd(b, a % b) }
}
```

### クラスとオブジェクトの例

```tylang
// 銀行口座クラス
class BankAccount(initialBalance: Double) {
  var balance = initialBalance
  
  fun deposit(amount: Double): Unit {
    balance = balance + amount
  }
  
  fun withdraw(amount: Double): Boolean {
    if (amount <= balance) {
      balance = balance - amount
      true
    } else {
      false
    }
  }
  
  fun getBalance(): Double { balance }
}

// 数学ユーティリティオブジェクト
object MathLib {
  fun abs(x: Int): Int {
    if (x < 0) { -x } else { x }
  }
  
  fun power(base: Int, exp: Int): Int {
    if (exp == 0) { 1 } else { base * power(base, exp - 1) }
  }
  
  fun max(a: Int, b: Int): Int {
    if (a > b) { a } else { b }
  }
}
```

### 高度な例

```tylang
// ジェネリッククラス
class Stack<T> {
  private var items: List<T> = []
  
  fun push(item: T): Unit {
    items = item :: items
  }
  
  fun pop(): T {
    val head = items.head
    items = items.tail
    head
  }
  
  fun isEmpty(): Boolean {
    items.isEmpty()
  }
}

// 関数型プログラミングスタイル
object ListUtils {
  fun map<T, U>(list: List<T>, f: (T) => U): List<U> {
    if (list.isEmpty()) {
      []
    } else {
      f(list.head) :: map(list.tail, f)
    }
  }
  
  fun filter<T>(list: List<T>, predicate: (T) => Boolean): List<T> {
    if (list.isEmpty()) {
      []
    } else if (predicate(list.head)) {
      list.head :: filter(list.tail, predicate)
    } else {
      filter(list.tail, predicate)
    }
  }
}
```

### 拡張メソッドの活用例

```tylang
// リスト拡張
extension List<T> {
  fun length(): Int {
    if (this.isEmpty()) { 0 } else { 1 + this.tail.length() }
  }
  
  fun contains(item: T): Boolean {
    if (this.isEmpty()) {
      false
    } else if (this.head == item) {
      true
    } else {
      this.tail.contains(item)
    }
  }
}

// 文字列拡張
extension String {
  fun toUpperCase(): String {
    // ネイティブ実装を想定
    this.toUpperCase()
  }
  
  fun startsWith(prefix: String): Boolean {
    this.substring(0, prefix.length()) == prefix
  }
}

// 使用例
val numbers = [1, 2, 3, 4, 5]
val length = numbers.length()
val hasThree = numbers.contains(3)

val greeting = "Hello, World!"
val upper = greeting.toUpperCase()
val startsWithHello = greeting.startsWith("Hello")
```

## エラーハンドリング

```tylang
// 現在の実装では基本的なエラーメッセージ
// 将来的にはResult型やException型を追加予定

fun safeDivision(a: Int, b: Int): Int {
  if (b == 0) {
    // 現状ではランタイムエラー
    throw DivisionByZeroError("Division by zero")
  } else {
    a / b
  }
}
```

## 型注釈の省略

TyLangでは多くの場合で型注釈を省略できます：

```tylang
// 型推論が働く
val number = 42              // Int
val pi = 3.14159             // Double
val greeting = "Hello"       // String
val flag = true              // Boolean

// 関数の戻り値型も推論可能
fun square(x: Int) {
  x * x  // Int -> Int と推論
}

// ただし、パブリック関数では明示推奨
fun publicApi(input: String): Result<Data> {
  // 明確なインターフェース
}
```

## ベストプラクティス

### 命名規則

- **関数・変数**: `camelCase`
- **クラス・トレイト**: `PascalCase`
- **定数**: `UPPER_SNAKE_CASE`
- **パッケージ**: `lowercase`

### コードスタイル

```tylang
// 良い例
fun calculateArea(width: Double, height: Double): Double {
  width * height
}

class Rectangle(width: Double, height: Double) {
  fun area(): Double { width * height }
  fun perimeter(): Double { 2 * (width + height) }
}

// 悪い例（型注釈なし、名前が不明確）
fun calc(w, h) { w * h }
```

### 関数設計

```tylang
// 純粋関数を推奨
fun add(x: Int, y: Int): Int { x + y }  // 良い

// 副作用のある関数は明確に
fun logAndAdd(x: Int, y: Int): Int {
  println(s"Adding $x and $y")  // 副作用
  x + y
}
```

## 今後の拡張予定

1. **例外処理**: `try-catch-finally`
2. **パターンマッチング**: `match-case`
3. **モジュールシステム**: `import-package`
4. **非同期プログラミング**: `async-await`
5. **マクロシステム**: コンパイル時メタプログラミング
6. **標準ライブラリ**: 豊富なコレクション・I/O・文字列操作

## まとめ

TyLangは静的型付けと構造的サブタイピングを組み合わせた、モダンで表現力豊かなプログラミング言語です。型安全性を保ちながら、柔軟で読みやすいコードを書くことができます。

JVMをターゲットとすることで、既存のJavaエコシステムとの互換性を保ちながら、新しい言語機能を提供します。
