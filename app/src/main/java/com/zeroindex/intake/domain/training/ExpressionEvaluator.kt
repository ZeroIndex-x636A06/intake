package com.zeroindex.intake.domain.training

sealed interface ExpressionResult {
    data class Value(val value: Double) : ExpressionResult
    /** Message is written for the program builder, not for a log. */
    data class Error(val message: String) : ExpressionResult
}

/**
 * SPEC §10's CUSTOM rule: a small expression evaluator over the variables
 * `weight`, `reps`, `sets`, `completedReps`, `rpe`, `failureCount` and `week`,
 * supporting arithmetic, comparisons and if/else. Deliberately not a language.
 *
 * Grammar, recursive descent over about twenty token kinds:
 *
 *     expr    := "if" "(" expr ")" expr "else" expr | compare
 *     compare := sum (("<" | "<=" | ">" | ">=" | "==" | "!=") sum)?
 *     sum     := product (("+" | "-") product)*
 *     product := unary (("*" | "/") unary)*
 *     unary   := "-" unary | primary
 *     primary := NUMBER | IDENT | "(" expr ")" | ("min" | "max" | "round") "(" args ")"
 *
 * Comparisons evaluate to 1.0 or 0.0 so they compose with arithmetic.
 *
 * It never throws: every malformed rule comes back as [ExpressionResult.Error]
 * with something a person can act on, which is what keeps a bad rule from
 * taking the app down mid-workout.
 */
object ExpressionEvaluator {

    private const val MAX_DEPTH = 32
    private const val MAX_LENGTH = 500

    val SUPPORTED_VARIABLES = listOf(
        "weight", "reps", "sets", "completedReps", "rpe", "failureCount", "week",
    )

    fun evaluate(expression: String, variables: Map<String, Double>): ExpressionResult = try {
        when {
            expression.isBlank() -> ExpressionResult.Error("The rule is empty.")
            expression.length > MAX_LENGTH ->
                ExpressionResult.Error("The rule is too long (limit $MAX_LENGTH characters).")
            else -> {
                val tokens = Lexer(expression).tokenize()
                val parser = Parser(tokens, variables)
                val value = parser.parseExpression(depth = 0)
                when {
                    !parser.atEnd() ->
                        ExpressionResult.Error(
                            "Unexpected \"${parser.peekText()}\" after the end of the rule.",
                        )
                    value.isNaN() -> ExpressionResult.Error("The rule did not produce a number.")
                    value.isInfinite() -> ExpressionResult.Error("The rule overflowed.")
                    else -> ExpressionResult.Value(value)
                }
            }
        }
    } catch (e: ExpressionException) {
        ExpressionResult.Error(e.message ?: "That rule could not be read.")
    } catch (e: Exception) {
        // Belt and braces: a malformed rule must never reach a crash reporter.
        ExpressionResult.Error("That rule could not be read.")
    }

    /** Validates without needing real values, for the builder's error display. */
    fun validate(expression: String): ExpressionResult =
        evaluate(expression, SUPPORTED_VARIABLES.associateWith { 1.0 })

    private class ExpressionException(message: String) : Exception(message)

    private enum class Kind {
        NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, LPAREN, RPAREN, COMMA,
        LT, LTE, GT, GTE, EQ, NEQ, IF, ELSE, END,
    }

    private data class Token(val kind: Kind, val text: String, val number: Double = 0.0)

    private class Lexer(private val source: String) {
        private var index = 0

        fun tokenize(): List<Token> {
            val tokens = mutableListOf<Token>()
            while (index < source.length) {
                val c = source[index]
                when {
                    c.isWhitespace() -> index++
                    c.isDigit() || c == '.' -> tokens += number()
                    c.isLetter() || c == '_' -> tokens += identifier()
                    else -> tokens += operator()
                }
            }
            tokens += Token(Kind.END, "end of rule")
            return tokens
        }

        private fun number(): Token {
            val start = index
            var seenDot = false
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                if (source[index] == '.') {
                    if (seenDot) throw ExpressionException("\"${source.substring(start, index + 1)}\" is not a number.")
                    seenDot = true
                }
                index++
            }
            val text = source.substring(start, index)
            val value = text.toDoubleOrNull()
                ?: throw ExpressionException("\"$text\" is not a number.")
            return Token(Kind.NUMBER, text, value)
        }

        private fun identifier(): Token {
            val start = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) {
                index++
            }
            val text = source.substring(start, index)
            return when (text) {
                "if" -> Token(Kind.IF, text)
                "else" -> Token(Kind.ELSE, text)
                else -> Token(Kind.IDENT, text)
            }
        }

        private fun operator(): Token {
            val c = source[index]
            val next = source.getOrNull(index + 1)
            fun two(kind: Kind, text: String): Token {
                index += 2
                return Token(kind, text)
            }
            fun one(kind: Kind): Token {
                index += 1
                return Token(kind, c.toString())
            }
            return when {
                c == '+' -> one(Kind.PLUS)
                c == '-' -> one(Kind.MINUS)
                c == '*' -> one(Kind.STAR)
                c == '/' -> one(Kind.SLASH)
                c == '(' -> one(Kind.LPAREN)
                c == ')' -> one(Kind.RPAREN)
                c == ',' -> one(Kind.COMMA)
                c == '<' && next == '=' -> two(Kind.LTE, "<=")
                c == '>' && next == '=' -> two(Kind.GTE, ">=")
                c == '=' && next == '=' -> two(Kind.EQ, "==")
                c == '!' && next == '=' -> two(Kind.NEQ, "!=")
                c == '<' -> one(Kind.LT)
                c == '>' -> one(Kind.GT)
                c == '=' -> throw ExpressionException("Use == to compare, not =.")
                else -> throw ExpressionException("\"$c\" is not something this rule can use.")
            }
        }
    }

    private class Parser(
        private val tokens: List<Token>,
        private val variables: Map<String, Double>,
    ) {
        private var position = 0

        fun atEnd(): Boolean = peek().kind == Kind.END
        fun peekText(): String = peek().text

        fun parseExpression(depth: Int): Double {
            if (depth > MAX_DEPTH) throw ExpressionException("The rule nests too deeply.")
            if (peek().kind == Kind.IF) return parseIf(depth)
            return parseComparison(depth)
        }

        private fun parseIf(depth: Int): Double {
            expect(Kind.IF)
            expect(Kind.LPAREN, "if needs a condition in brackets, e.g. if (reps > 8) ...")
            val condition = parseExpression(depth + 1)
            expect(Kind.RPAREN, "the if condition is missing its closing bracket")
            val whenTrue = parseExpression(depth + 1)
            expect(Kind.ELSE, "every if needs an else")
            val whenFalse = parseExpression(depth + 1)
            return if (condition != 0.0) whenTrue else whenFalse
        }

        private fun parseComparison(depth: Int): Double {
            val left = parseSum(depth)
            val kind = peek().kind
            val isComparison = kind in setOf(Kind.LT, Kind.LTE, Kind.GT, Kind.GTE, Kind.EQ, Kind.NEQ)
            if (!isComparison) return left
            advance()
            val right = parseSum(depth)
            val result = when (kind) {
                Kind.LT -> left < right
                Kind.LTE -> left <= right
                Kind.GT -> left > right
                Kind.GTE -> left >= right
                Kind.EQ -> left == right
                Kind.NEQ -> left != right
                else -> false
            }
            return if (result) 1.0 else 0.0
        }

        private fun parseSum(depth: Int): Double {
            var value = parseProduct(depth)
            while (true) {
                when (peek().kind) {
                    Kind.PLUS -> { advance(); value += parseProduct(depth) }
                    Kind.MINUS -> { advance(); value -= parseProduct(depth) }
                    else -> return value
                }
            }
        }

        private fun parseProduct(depth: Int): Double {
            var value = parseUnary(depth)
            while (true) {
                when (peek().kind) {
                    Kind.STAR -> { advance(); value *= parseUnary(depth) }
                    Kind.SLASH -> {
                        advance()
                        val divisor = parseUnary(depth)
                        if (divisor == 0.0) throw ExpressionException("The rule divides by zero.")
                        value /= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseUnary(depth: Int): Double {
            if (peek().kind == Kind.MINUS) {
                advance()
                return -parseUnary(depth)
            }
            return parsePrimary(depth)
        }

        private fun parsePrimary(depth: Int): Double {
            if (depth > MAX_DEPTH) throw ExpressionException("The rule nests too deeply.")
            val token = peek()
            return when (token.kind) {
                Kind.NUMBER -> { advance(); token.number }
                Kind.LPAREN -> {
                    advance()
                    val value = parseExpression(depth + 1)
                    expect(Kind.RPAREN, "a bracket is never closed")
                    value
                }
                Kind.IDENT -> {
                    advance()
                    when (token.text) {
                        "min", "max", "round" -> parseFunction(token.text, depth)
                        else -> variables[token.text]
                            ?: throw ExpressionException(
                                "\"${token.text}\" is not a variable this rule can use. " +
                                    "Available: ${SUPPORTED_VARIABLES.joinToString(", ")}.",
                            )
                    }
                }
                Kind.END -> throw ExpressionException("The rule ends before it finishes.")
                else -> throw ExpressionException("\"${token.text}\" is not valid here.")
            }
        }

        private fun parseFunction(name: String, depth: Int): Double {
            expect(Kind.LPAREN, "$name needs brackets")
            val first = parseExpression(depth + 1)
            return when (name) {
                "round" -> {
                    expect(Kind.RPAREN, "round takes one value")
                    kotlin.math.round(first)
                }
                else -> {
                    expect(Kind.COMMA, "$name takes two values separated by a comma")
                    val second = parseExpression(depth + 1)
                    expect(Kind.RPAREN, "$name is missing its closing bracket")
                    if (name == "min") minOf(first, second) else maxOf(first, second)
                }
            }
        }

        private fun peek(): Token = tokens.getOrElse(position) { tokens.last() }

        private fun advance() { if (position < tokens.size - 1) position++ }

        private fun expect(kind: Kind, hint: String? = null) {
            if (peek().kind != kind) {
                throw ExpressionException(
                    hint?.replaceFirstChar(Char::uppercase)?.plus(".")
                        ?: "Expected something else at \"${peek().text}\".",
                )
            }
            advance()
        }
    }
}
