// "Convert string to character literal" "true"
fun test(c: Char): Boolean {
    return <caret>"\t" == c
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix
/* IGNORE_K2 */