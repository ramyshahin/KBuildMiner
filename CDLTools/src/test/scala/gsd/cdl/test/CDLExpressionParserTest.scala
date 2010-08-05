/*
 * Copyright (c) 2010 Steven She <shshe@gsd.uwaterloo.ca> and
 * Thorsten Berger <berger@informatik.uni-leipzig.de>
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
package gsd.cdl.test

import gsd.cdl.CDLExpression._

import gsd.cdl.model._
import org.junit.runner.RunWith
import org.scalatest.junit.{JUnitRunner}
import org.scalatest.FunSuite

@RunWith(classOf[JUnitRunner])
class CDLExpressionParserTest extends FunSuite {

  implicit def toIdentifier(s : String) = Identifier(s)

  test( "conditionals" ){
    assert(parseString("a ? b : c") === Conditional("a", "b", "c"))
    assert(parseString("a ? b : c ? d : e") === Conditional("a", "b", Conditional("c", "d", "e")))
  }

  test( "inequality" ){
    assert(parseString("a < b > c >= d <= e") === LessThanOrEq(GreaterThanOrEq(GreaterThan(LessThan("a", "b"), "c"), "d"), "e"))
  }

}