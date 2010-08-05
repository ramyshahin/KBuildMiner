/*
 * Copyright (c) 2010 Steven She <shshe@gsd.uwaterloo.ca>
 * and Thorsten Berger <berger@informatik.uni-leipzig.de>
 *
 * This file is part of CDLTools.
 *
 * CDLTools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CDLTools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CDLTools.  If not, see <http://www.gnu.org/licenses/>.
 */
package gsd.cdl

import model._
import util.parsing.combinator._

//TODO test precedence of operations (especially conditional)
//TODO use flatten2 for constructing expression
trait CDLExpressionParser extends JavaTokenParsers with ImplicitConversions {

  //Adds support to stringLiteral for escaping quotes
  val trueExp = """true""".r ^^ ( x => True() )
  val falseExp = """false""".r ^^ ( x => False() ) 
  val identifier = """[_a-zA-Z][-_0-9a-zA-Z]*""".r ^^ Identifier
  val lit = ("\""+"""([^"\p{Cntrl}\\]|\\[\\/bfnrt"])*"""+"\"").r ^^ StringLiteral

  val integer : Parser[IntLiteral] =
      "(-)?[0-9]+".r ^^ { case i =>
        try {
          IntLiteral(i.toInt)
        }
        catch {
          case e: NumberFormatException =>
//            println("WARNING: " + e.getMessage + ", rounding to " + Integer.MAX_VALUE)
            IntLiteral(Integer.MAX_VALUE)
        }
      }
//  val integer : Parser[StringLiteral] =
//      "(-)?[0-9]+".r ^^ StringLiteral
  val hex : Parser[StringLiteral] =
      //FIXME currently returns a string
      "0x[0-9a-fA-F]+".r  ^^ StringLiteral

  // FIXME float currently doesn't work, fix later...
  val float : Parser[StringLiteral] =
      """[1-9]\.[0-9]*e+[0-9]+""".r ^^ StringLiteral

  lazy val expr : Parser[CDLExpression] = condExpr

  lazy val condExpr : Parser[CDLExpression] =
    orExpr ~ rep("?" ~> condExpr ~ (":" ~> orExpr)) ^^
      {
        case first~rest => (first /: rest){ case (cond, t~f) =>
          Conditional(cond, t, f)
        }
      }

  lazy val orExpr : Parser[CDLExpression] =
    andExpr ~ ("||" ~> orExpr) ^^ Or | andExpr

  lazy val andExpr : Parser[CDLExpression] =
    eqExpr ~ ("&&" ~> andExpr) ^^ And | eqExpr

  //lazy val spaceExpr =
  //  eqExpr ~ expr ^^ AndExpression | eqExpr

  lazy val eqExpr : Parser[CDLExpression] =
    neqExpr ~ ("==" ~> eqExpr) ^^ Eq | neqExpr

  lazy val neqExpr : Parser[CDLExpression] =
    ineqExpr ~ ("!=" ~> neqExpr) ^^ NEq | ineqExpr

  lazy val ineqExpr =
    arithExpr ~ rep(("<(=)?".r | ">(=)?".r) ~ arithExpr) ^^
      {
        case first~rest => (first /: rest){ case (l, op~r) =>
          op match {
            case "<" => LessThan(l,r)
            case ">" => GreaterThan(l,r)
            case "<=" => LessThanOrEq(l,r)
            case ">=" => GreaterThanOrEq(l,r)
          }
        }
      }

  lazy val arithExpr =
    multExpr ~ rep(("."|"+"|"-") ~ multExpr) ^^
      {
        case first~rest => (first /: rest){ case (l, op~r) =>
          op match {
            case "." => Dot(l,r)
            case "-" => Minus(l,r)
            case "+" => Plus(l,r)
          }
        }
      }

  lazy val multExpr =
  unaryExpr ~ rep(("*"|"/"|"%") ~ unaryExpr) ^^
          {
            case first~rest => (first /: rest){ case (l, op~r) =>
              op match {
                case "*" => Times(l,r)
                case "/" => Div(l,r)
                case "%" => Mod(l,r)
              }
            }
          }

  lazy val unaryExpr =
    rep("!" ^^^ { Not.apply _ } | "--" ^^^ { MinusMinus.apply _ }) ~ funcExpr ^^ {
      case unarys~e => (unarys :\ e) { case (op,r) => op(r) }
    }


  lazy val funcExpr : Parser[CDLExpression] =
    identifier ~ ("(" ~> opt(funcExpr ~ rep("," ~> funcExpr)) <~ ")") ^^
      {
        case Identifier(name)~Some(first~rest) =>
            FunctionCall(name, first :: rest)
        case res => error("Unexpected result: " + res)
      } | factorExpr

  lazy val factorExpr = "(" ~> expr <~ ")" | atomExpr
  lazy val atomExpr = hex | float | lit | integer | trueExp | falseExp | identifier

}

object CDLExpression extends CDLExpressionParser {

  def parseString(s : String) = parseAll(expr, s) match {
    case Success(result,_) => result
    case x => error(x.toString)
  }

}