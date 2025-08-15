import java.io.File
import kotlin.collections.ArrayDeque
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.engine.Format

// ---------------------------------------------------------
// Laboratorio 2
// ---------------------------------------------------------
// Lista completa de operadores
val ALL_OPERATORS = setOf('|', '.', '?', '*', '+', '^')
// Operadores que actuan sobre 1 simbolo
val UNARY_OPERATORS = setOf('*', '+', '?')
// Operadores que actuan sobre 2 simbolos
val BINARY_OPERATORS = setOf('|', '^', '.')

// Precedencia de operadores
fun getPrecedence(operatorChar: Char): Int {
    return when (operatorChar) {
        '(' -> 1
        '|' -> 2
        '.' -> 3
        '?', '*', '+' -> 4
        '^' -> 5
        else -> 0
    }
}

// Manejar clases
// Metodo para retornar una clase tal cual como viene
// y saber el indice en el que seguimos inmediatamente despues del ]
fun readClass(pattern: String, startIndex: Int): Pair<String, Int> {
    val sb = StringBuilder()
    var i = startIndex
    sb.append('[')
    i += 1
    while (i < pattern.length) {
        val ch = pattern[i]
        if (ch == '\\' && i + 1 < pattern.length) {
            sb.append('\\').append(pattern[i + 1])
            i += 2
            continue
        }
        sb.append(ch)
        i += 1
        if (ch == ']') break
    }
    return sb.toString() to i
}

// Formateo de la regex 
// Insertar concatenaciones entre simbolos con el '.'
// Mantiene clases y escape characters como unidades
fun formatRegex(rawRegex: String): String {
    val formattedRegexBuilder = StringBuilder()

    var index = 0

    while (index < rawRegex.length) {
        val currentChar = rawRegex[index]

        if (currentChar == '[') {
            val (charClass, classEndIndex) = readClass(rawRegex, index)
            formattedRegexBuilder.append(charClass)

            if (classEndIndex < rawRegex.length) {
                val nextChar = rawRegex[classEndIndex]
                val concatNecesaria =
                    (nextChar != ')') &&
                    (nextChar !in ALL_OPERATORS) &&
                    (nextChar != '\\') &&
                    (nextChar != ']')
                if (concatNecesaria) {
                    formattedRegexBuilder.append('.')
                }
            }
            index = classEndIndex
            continue
        }

        if (currentChar == '\\' && index + 1 < rawRegex.length) {
            formattedRegexBuilder.append('\\')
            formattedRegexBuilder.append(rawRegex[index + 1])

            // Posible concatenación tras el escape
            if (index + 2 < rawRegex.length) {
                val nextChar = rawRegex[index + 2]
                if (nextChar != ')' && nextChar !in ALL_OPERATORS && nextChar != '\\') {
                    formattedRegexBuilder.append('.')
                }
            }
            index += 2
            continue
        }

        if (currentChar == '.') {
            // Normalizamos a '\.' como token literal
            formattedRegexBuilder.append('\\').append('.')
            if (index + 1 < rawRegex.length) {
                val nextChar = rawRegex[index + 1]
                val concatNecesaria =
                    (nextChar != ')') &&
                    (nextChar !in ALL_OPERATORS) &&
                    (nextChar != '\\')
                if (concatNecesaria) {
                    formattedRegexBuilder.append('.')
                }
            }
            index += 1
            continue
        }

        formattedRegexBuilder.append(currentChar)

        if (index + 1 < rawRegex.length) {
            val nextChar = rawRegex[index + 1]
            val concatNecesaria =
                (currentChar != '(') &&
                (nextChar != ')') &&
                (nextChar !in ALL_OPERATORS) &&
                (currentChar !in BINARY_OPERATORS) ||
                (currentChar in UNARY_OPERATORS && (nextChar == '(' || nextChar == '\\' || (nextChar !in ALL_OPERATORS && nextChar != ')')))
            if (concatNecesaria) {
                formattedRegexBuilder.append('.')
            }
        }
        index++
    }
    return formattedRegexBuilder.toString()
}

// Conversión de regex infija a posfija
// Shunting yard
fun infixToPostfix(regex: String): String {
    val postfixBuilder = StringBuilder()
    val operatorStack = ArrayDeque<Char>()

    // Colocar concatenacion explicita
    val formattedRegex = formatRegex(regex)

    var index = 0

    while (index < formattedRegex.length) {
        val currentChar = formattedRegex[index]

        // Manejar como un solo token si es clase
        if (currentChar == '[') {
            val (charClass, j) = readClass(formattedRegex, index)
            postfixBuilder.append(charClass)
            index = j
            continue
        }

        // los \ se van directos al output
        if (currentChar == '\\' && index + 1 < formattedRegex.length) {
            postfixBuilder.append('\\').append(formattedRegex[index + 1])
            index += 2
            continue
        }

        // Unarios postfix emitir directo al output, asi dice el pseudocodigo
        if (currentChar in UNARY_OPERATORS) {
            postfixBuilder.append(currentChar)
            index++
            continue
        }

        when {
            // parentesis de apertura directo a la pila
            currentChar == '(' -> {
                operatorStack.addFirst(currentChar)
                index++
            }
            // Antes de apilarlo, se saca de la pila cualquier operador que este adentro
            //que tenga mayor o igual precedencia que el actual
            currentChar in ALL_OPERATORS -> {
                while (operatorStack.isNotEmpty() && operatorStack.first() != '(' && getPrecedence(operatorStack.first()) >= getPrecedence(currentChar)) {
                    val operator = operatorStack.removeFirst()
                    postfixBuilder.append(operator)
                }
                operatorStack.addFirst(currentChar)
                index++
            }
            // Se desapila hasta encontrar un '('
            currentChar == ')' -> {
                while (operatorStack.isNotEmpty() && operatorStack.first() != '(') {
                    val operator = operatorStack.removeFirst()
                    postfixBuilder.append(operator)
                }
                if (operatorStack.isNotEmpty() && operatorStack.first() == '(' ) {
                    operatorStack.removeFirst() // descartar (
                }
                index++
            }
            else -> {
                // Simbolo
                postfixBuilder.append(currentChar)
                index++
            }
        }
    }

    while (operatorStack.isNotEmpty()) {
        val topOperator = operatorStack.removeFirst()
        if (topOperator == '(') {
            throw IllegalArgumentException("Paréntesis desbalanceados: falta ')'")
        }
        postfixBuilder.append(topOperator)
    }

    return postfixBuilder.toString()
}
// ---------------------------------------------------------------

// Laboratorio 3 -------------------------------------------------
// Sustituciones de Operadores
// Pasar de X? a algo como (X|ε)
fun sustituirOpcional(pattern: String): String {
    val outputBuilder = StringBuilder()
    var index = 0
    while (index < pattern.length) {
        val currentChar = pattern[index]
        if (currentChar == '?') {
            // Obtener el operando inmediatamente anterior
            val builtSoFar = outputBuilder.toString()
            var start = builtSoFar.length - 1
            val operand = when {
                // Grupo entre paréntesis
                builtSoFar[start] == ')' -> {
                    var depth = 1
                    start--
                    while (start >= 0 && depth > 0) {
                        if (builtSoFar[start] == ')') depth++
                        if (builtSoFar[start] == '(') depth--
                        start--
                    }
                    builtSoFar.substring(start + 1)
                }
                // Clase de caracteres 
                builtSoFar[start] == ']' -> {
                    start--
                    while (start >= 0 && builtSoFar[start] != '[') start--
                    builtSoFar.substring(start)
                }
                // Secuencia escapada
                start > 0 && builtSoFar[start - 1] == '\\' -> {
                    builtSoFar.substring(start - 1)
                }
                // Símbolo simple
                else -> {
                    builtSoFar.substring(start)
                }
            }
            // quitar el operando recién detectado
            val operandStartIndex = outputBuilder.length - operand.length
            outputBuilder.setLength(operandStartIndex)
            outputBuilder.append("(").append(operand).append("|ε)")
            index++
        } else {
            outputBuilder.append(currentChar)
            index++
        }
    }
    return outputBuilder.toString()
}


// Si encontramos un + como operador
// hay que trabajarlo de a+ a algo como aa*
fun sustituirMas(pattern: String): String {
    val outputBuilder = StringBuilder()
    var index = 0
    while (index < pattern.length) {
        val currentChar = pattern[index]
        if (currentChar == '+') {
            // Obtener el operando justo antes de '+'
            val builtSoFar = outputBuilder.toString()
            var start = builtSoFar.length - 1
            val operand = when {
                // Grupo entre paréntesis (...)
                builtSoFar[start] == ')' -> {
                    var depth = 1
                    start--
                    while (start >= 0 && depth > 0) {
                        if (builtSoFar[start] == ')') depth++
                        if (builtSoFar[start] == '(') depth--
                        start--
                    }
                    builtSoFar.substring(start + 1)
                }
                // Clase de caracteres [...]
                builtSoFar[start] == ']' -> {
                    start--
                    while (start >= 0 && builtSoFar[start] != '[') start--
                    builtSoFar.substring(start, builtSoFar.length)
                }
                // Secuencia escapada (\x)
                start > 0 && builtSoFar[start - 1] == '\\' -> builtSoFar.substring(start - 1)
                else -> builtSoFar.substring(start)
            }
            // Expandir operando+ a operando(operando)*
            outputBuilder.append('(')
            outputBuilder.append(operand)
            outputBuilder.append(')')
            outputBuilder.append('*')
            index++
        } else {
            outputBuilder.append(currentChar)
            index++
        }
    }
    return outputBuilder.toString()
}


// Nodo de Arbol --------------------------------------------------------------------------
data class TreeNode<T>(
  val value: T,
  val children: MutableList<TreeNode<T>> = mutableListOf()
) {
  fun addChild(node: TreeNode<T>) {
    children.add(node)
  }
}

// Tokeniza la salida postfix en una lista de tokens (clases, escapes y símbolos)
fun tokenize(postfixStr: String): List<String> {
    val tokens = mutableListOf<String>()
    var index = 0
    while (index < postfixStr.length) {
        when {
            postfixStr[index] == '[' -> {
                // Clase de caracteres
                val sb = StringBuilder().append('[')
                index++
                while (index < postfixStr.length) {
                    sb.append(postfixStr[index])
                    if (postfixStr[index] == ']') { index++; break }
                    index++
                }
                tokens.add(sb.toString())
            }
            postfixStr[index] == '\\' && index + 1 < postfixStr.length -> {
                // Escape
                tokens.add(postfixStr.substring(index, index + 2))
                index += 2
            }
            else -> {
                // Un solo carácter
                tokens.add(postfixStr[index].toString())
                index++
            }
        }
    }
    return tokens
}

// Generar DOT para el arbol -- TERMINAR DE ENTENER
fun generateDot(root: TreeNode<String>): String {
    val dotBuilder = StringBuilder()
    dotBuilder.appendLine("digraph AST {")
    dotBuilder.appendLine("  node [shape=circle];")
  
    // Genera nodos únicos
    fun visit(node: TreeNode<String>, nodeId: Int, generateNextId: () -> Int) {
      dotBuilder.appendLine("  n$nodeId [label=\"${node.value}\"];")
      for (child in node.children) {
        val childId = generateNextId()
        dotBuilder.appendLine("  n$nodeId -> n$childId;")
        visit(child, childId, generateNextId)
      }
    }
  
    var nodeIdCounter = 0
    visit(root, nodeIdCounter) { ++nodeIdCounter }
    dotBuilder.appendLine("}")
    return dotBuilder.toString()
}

fun renderDot(dotSource: String, outputFile: File) {
    Graphviz
      .fromString(dotSource)
      .render(Format.PNG)      
      .toFile(outputFile)      
}

// LABORATORIO 4 -------------------------------------------------
// Base
class State(
    val id: Int,
    val next_states: MutableList<NextState> = mutableListOf(),
    var isAccepted: Boolean = false
) {
    override fun equals(other: Any?): Boolean =
        other is State && this.id == other.id

    override fun hashCode(): Int = id
}

data class NextState(
    val movedToState: State,
    val symbol: Char? = null, //simbolo de entrada, null por los epsilom
    val charSet: Set<Char>? = null // para [clases]
)

data class Fragment(
    val initial_state: State,
    val final_state: State
)
//--------------------------------------------------------------
// Utils para crear estados y aristas
object NfaId {
    private var next = 0 // Llevar track del current id que se trabaje
    fun newId() = next++
    fun reset() { next = 0}
}

fun newState(accepted: Boolean = false) = State(NfaId.newId(), isAccepted = accepted)

fun addEpsilonTransition(from: State, to: State){
    from.next_states += NextState(to, null, null)
}

fun addTransition(from: State, to: State, symbol: Char){
    from.next_states += NextState(to, symbol, null)
}

fun addCharSetTransition(from: State, to: State, charSet: Set<Char>){
    from.next_states += NextState(to, null, charSet)
}
//====================================================================
// Simbolo literal
fun literalFragment(symbol: Char): Fragment{
    val initialState = newState()
    val final_state = newState(true)

    addTransition(from = initialState, to = final_state, symbol = symbol)

    return Fragment(initialState, final_state)
}

// Clase de caracteres (validar si se maneja con ORs)
fun classFragment(charSet: Set<Char>): Fragment{
    val initialState = newState()
    val final_state = newState(true)

    addCharSetTransition(from = initialState, to = final_state, charSet = charSet)

    return Fragment(initialState, final_state)
}

// Concatenacion
fun concatFragment(initFragment: Fragment, finalFragment: Fragment): Fragment {
    //El final del initial fragment deja de ser el final aka ya no es aceptacion
    initFragment.final_state.isAccepted = false
    addEpsilonTransition(initFragment.final_state, finalFragment.initial_state)
    return Fragment(initFragment.initial_state, finalFragment.final_state)
}

// Union
fun unionFragment(upperFragment: Fragment, lowerFragment: Fragment): Fragment {
    val initialState = newState()
    val finalState = newState(true)

    // Los finales de los fragments anteriores se vuelven inaceptables
    upperFragment.final_state.isAccepted = false
    lowerFragment.final_state.isAccepted = false

    // Transiciones
    addEpsilonTransition(initialState, upperFragment.initial_state)
    addEpsilonTransition(initialState, lowerFragment.initial_state)

    addEpsilonTransition(upperFragment.final_state, finalState)
    addEpsilonTransition(lowerFragment.final_state, finalState)

    return Fragment(initialState, finalState)
}

// Kleene 
fun kleeneFragment(fragment: Fragment): Fragment {
    val initialState = newState()
    val finalState = newState(true)

    // Resetear el final state del fragment a que no sea de aceptacion
    fragment.final_state.isAccepted = false

    // Nuevo inicial al inicial del fragment
    addEpsilonTransition(initialState, fragment.initial_state)

    // Final del gragment, hacia su inicial
    addEpsilonTransition(fragment.final_state, fragment.initial_state)

    // Final del fragment, al final del que se esta construyendo
    addEpsilonTransition(fragment.final_state, finalState)

    /// del initial directo al final
    addEpsilonTransition(initialState, finalState)

    return Fragment(initialState, finalState)
}
// =========================================================
private const val EPSILON_STR = "ε"

fun parseCharClass(token: String): Set<Char> {
    require(token.startsWith("[") && token.endsWith("]")) { "Clase inválida: $token" }
    val set = mutableSetOf<Char>()
    var i = 1
    while (i < token.length - 1) {
        val ch = token[i]
        if (ch == '\\' && i + 1 < token.length - 1) {
            set += token[i + 1]
            i += 2
        } else if (i + 2 < token.length - 1 && token[i + 1] == '-') {
            // rango a-b
            val start = ch
            val end = token[i + 2]
            require(start <= end) { "Rango inválido en clase: $token" }
            for (c in start..end) set += c
            i += 3
        } else {
            set += ch
            i += 1
        }
    }
    return set
}

fun buildNFA(tokens: List<String>): Fragment {
    val stack = ArrayDeque<Fragment>()

    fun isTokenUnary(token: String) = token.length == 1 && token[0] in UNARY_OPERATORS
    fun isTokenBinary(token: String) = token.length == 1 && token[0] in BINARY_OPERATORS

    for(token in tokens) {
        when {
            isTokenBinary(token) -> {
                //Sacar los 2 fragments
                val right = stack.removeFirst()
                val left = stack.removeFirst()

                // Usar funcion acorde a operador
                val fragment = when(token[0]){
                    '.' -> concatFragment(left, right)
                    '|' -> unionFragment(left, right)
                    else -> error("Hubo un error")
                }

                //Meter el fragmento resultante
                stack.addFirst(fragment)
            }

            isTokenUnary(token) -> {
                val fragment = stack.removeFirst()

                val newFragment = when(token[0]){
                    '*' -> kleeneFragment(fragment)
                    else -> error("Ni tendria que venir, se tuvo que sustituir")
                }

                stack.addFirst(newFragment)
            }

            else -> {
                val fragment = when {
                    // va a venir por la sustitucion de ?
                    token == "ε" -> {
                        val initialState = newState()
                        val finalState = newState(true)
                        addEpsilonTransition(initialState, finalState)
                        Fragment(initialState, finalState)
                    }

                    token.startsWith("\\") && token.length == 2 -> {
                        literalFragment(token[1]) //slatarse el scape char
                    }

                    token.startsWith("[") && token.endsWith("]") -> {
                        val charSet = parseCharClass(token)
                        classFragment(charSet)
                    }
                    token.length == 1 -> {
                        literalFragment(token[0])
                    }
                    else -> error("Token invalido: $token")
                }
                
                stack.addFirst(fragment)
            }
        }
    }

    return stack.removeFirst()
}
// =========================================================

// ---------------------------------------------------------------
// DOT para visualizar NFA
private fun bfsRenumber(start: State): Map<Int, Int> {
    val order = mutableListOf<State>()
    val seen = mutableSetOf<Int>()
    val q = ArrayDeque<State>()
    q.addLast(start)
    seen.add(start.id)
    while (q.isNotEmpty()) {
        val s = q.removeFirst()
        order += s
        for (e in s.next_states) {
            val to = e.movedToState
            if (seen.add(to.id)) q.addLast(to)
        }
    }
    return order.withIndex().associate { (newId, st) -> st.id to newId }
}

fun generateNfaDot(nfa: Fragment): String {
    val sb = StringBuilder()
    sb.appendLine("digraph NFA {")
    sb.appendLine("  rankdir=LR;")
    sb.appendLine("  node [shape=circle];")

    // renumeración solo para mostrar
    val idMap = bfsRenumber(nfa.initial_state)
    fun show(id: Int) = idMap[id] ?: error("id no mapeado: $id")

    // flecha de inicio (siempre al 0)
    sb.appendLine("  __start__ [shape=point, label=\"\"];")
    sb.appendLine("  __start__ -> ${show(nfa.initial_state.id)};")

    // recorrer y emitir usando los ids "bonitos"
    val visited = mutableSetOf<Int>()
    val stack = ArrayDeque<State>()
    stack.addFirst(nfa.initial_state)

    fun labelOf(edge: NextState): String = when {
        edge.symbol != null   -> edge.symbol.toString()
        edge.charSet != null  -> "[" + edge.charSet.sorted().joinToString("") + "]"
        else                  -> "ε"
    }

    while (stack.isNotEmpty()) {
        val s = stack.removeFirst()
        if (!visited.add(s.id)) continue

        val shownId = show(s.id)
        if (s.isAccepted) {
            sb.appendLine("  $shownId [shape=doublecircle];")
        } else {
            sb.appendLine("  $shownId [shape=circle];")
        }

        for (e in s.next_states) {
            val toShown = show(e.movedToState.id)
            sb.appendLine("  $shownId -> $toShown [label=\"${labelOf(e)}\"];")
            if (e.movedToState.id !in visited) stack.addFirst(e.movedToState)
        }
    }

    sb.appendLine("}")
    return sb.toString()
}
//////////////////////////////////////////////
// (a*|b*)+
val FirstRegexStringsTest = listOf(
    "",
    "a",
    "b",
    "ab",
    "ba",
    "aa",
    "bb",
    "aabbaaab",
    "holamundo"
)

// ((ε|a)|b*)*
val SecondRegexStringsTest = listOf(
    "",
    "a",
    "b",
    "ab",
    "ba",
    "aa",
    "bb",
    "aabbaaab",
    "holamundo"
)

// (a|b)*abb(a|b)*
val ThirdRegexStringsTest = listOf(
    "", // NO
    "abb",
    "aaaabbbabbaaaabbb",
    "holamundo"
)

// 0?(1?)?0*
val FourthRegexStringsTest = listOf(
    "",
    "011", //NO
    "010",
    "010000",
    "00000",
    "1",
    "0",
    "holamundo",
)

val Tests = listOf(
    FirstRegexStringsTest,
    SecondRegexStringsTest,
    ThirdRegexStringsTest,
    FourthRegexStringsTest
)

//////////////////////////////////////////////
// E-clausure
fun epsilonClosure(states: Set<State>): Set<State>{
    val result = states.toMutableSet()
    val stack = ArrayDeque<State>()

    states.forEach { stack.addFirst(it) }

    while(stack.isNotEmpty()){
        val state = stack.removeFirst()
        for(nextState in state.next_states){
            if(nextState.symbol == null && nextState.charSet == null){
                if(result.add(nextState.movedToState)){
                    stack.addLast(nextState.movedToState)
                }
            }
        }
    }

    return result
}

// Move func
fun move(states: Set<State>, symbol: Char): Set<State>{
    val result = mutableSetOf<State>()
    for(state in states){
        for(nextState in state.next_states){
            if(nextState.symbol != null && nextState.symbol == symbol){
                result.add(nextState.movedToState)
            } else if (nextState.charSet != null && symbol in nextState.charSet){
                result.add(nextState.movedToState)
            }
        }
    }

    return result
}

// Determinar aceptacion
fun acceptsString(nfa: Fragment, input: String): Boolean {
    var currentState = epsilonClosure(setOf(nfa.initial_state))

    for(symbol in input){
        val next = move(currentState, symbol)
        currentState = if (next.isEmpty()) emptySet() else epsilonClosure(next)
        if (currentState.isEmpty()) break
    }

    return currentState.any { it.isAccepted }
}


//////////////////////////////////////////////
fun main() {
    val file = File("src/main/kotlin/AFN.txt")
    val lines = file.readLines()

    val formattedLines = ArrayList<String>()

    // Cambiar ? a |ε
    // Cambiar + a aa*
    for (line in lines) {
        var formattedLine = sustituirOpcional(line)
        formattedLine = sustituirMas(formattedLine)
        formattedLines.add(formattedLine)
    }

    println("Lineas formateadas: $formattedLines")

    // Construccion de AFN
    for((index, formattedLine) in formattedLines.withIndex()) {

        // infix a postfix
        val postfix = infixToPostfix(formattedLine)

        // tokenizar
        val tokens = tokenize(postfix)

        /// Constuir AFN
        NfaId.reset()
        val nfa = buildNFA(tokens)

        //Generar AFN - Grafo
        val dot = generateNfaDot(nfa)
        val dotFile = File("nfa_$index.png")
        renderDot(dot, dotFile)

        // Testear cadenas
        var test2Validate = Tests[index]
        for(test in test2Validate){
            val result = acceptsString(nfa, test)
            println("Cadena $test es ACEPTADA: ${if (result) "SÍ" else "NO"}")
        }
        println("----------------------------------------------------------")
    }
    
}
