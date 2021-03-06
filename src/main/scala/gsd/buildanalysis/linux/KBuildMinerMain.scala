/*
 * Copyright (c) 2010 Thorsten Berger <berger@informatik.uni-leipzig.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package gsd.buildanalysis.linux

import java.util.Properties
import model._
import gsd.common.Logging
import java.io.{PrintWriter, FileWriter, File, FileReader}
import org.antlr.runtime.{ANTLRInputStream, CommonTokenStream, ANTLRFileStream}
import org.kiama.rewriting.Rewriter._
import org.kiama.attribution.Attribution

/**
 * Run the KBuild miner with this main class.
 */
object KBuildMinerMain extends optional.Application with Logging with BuildMinerCommons{

  // just some defaults in case nothing is specified in the properties file or on command line
  val AST_OUTPUT = "output/makefile_tree.xml"
  val PC_OUTPUT = "output/presence_conditions.txt"
  val CCFLAGS_OUTPUT = "output/ccflags.xml"

//  /**
//   * Part of the API, to be used by other projects. Tries to identify the
//   * type of project in the given folder and calculates PCs for each file.
//   * @return boolean PCs
//   */
//  def calculatePCsForProject( codebase: String ): Map[String, Expression] = {
//    val p = ProjectFactory newProject codebase
//    val ast = buildAST( p )
//    val pcs = PCDerivation.calculateFilePCs( ast, Map(), p )
//    val boolPCs = pcs.map{ case (f,p) => (f, rewrite( toBoolean )(p) ) }.toList
//    Map( boolPCs: _* )
//  }

    /**
     *
     * @param codebase root folder of the project
     * @param topFolders comma separated list of folders or files to start with. folders are inspected for Makefile or Kconfig files within them
     * @param astOutput output file for ast
     * @param pcOutput output file for PCs
     * @param ccflagsOutput output files for cc flags
     * @param saveAST whether to store the AST
     */
  def main( codebase: String,
            topFolders: String,
            astOutput: Option[String],
            pcOutput: Option[String],
            ccflagsOutput: Option[String],
            saveAST: Option[String] ){

    val _codebase   = codebase
    val _astOutput  = getArg( astOutput, "astOutput", AST_OUTPUT )
    val _pcOutput   = getArg( pcOutput, "pcOutput", PC_OUTPUT )
    val _ccflagsOutput   = getArg( ccflagsOutput, "ccflagsOutput", CCFLAGS_OUTPUT )
    val _saveAST = getArg( saveAST, "saveAST", "true" )

        val _topFolders = topFolders.split(",").toList
        assert(!_topFolders.isEmpty, "no top folders provided")

    val p = new Project(_codebase, _topFolders)
    new File( "output/logs" ) mkdirs

    info( "Starting KBuildMiner..." )

    val ast = buildAST( p )

    if( _saveAST == "true" )
      PersistenceManager.outputXML( ast, _astOutput )

    info( "Deriving file presence conditions..." )
    val pcs = PCDerivation.calculateFilePCs( ast,Map(), p )

    val out = new PrintWriter( new FileWriter( _pcOutput ) )
    info( "Saving PCs to: " + _pcOutput )
    pcs.toList.sortWith( _._1 < _._1 ).foreach{ case (name,pc) =>
      out.println( name + ": " + PersistenceManager.pp( rewrite( removeCONFIG_Prefix)( pc ) ) )
    }
    out close

    // extract C flags
    info( "Extracting additional C flags..." )
    PersistenceManager.saveCFlags(
      rewrite( removeCONFIG_Prefix)( CFlagRecognition.findExtraCFlags( ast ) ),
      rewrite( removeCONFIG_Prefix)( CFlagRecognition.findFileSpecificFlags( ast ) ),
      _ccflagsOutput )
  }

  private def getArg[T]( arg: Option[T], name:String, default: String ): String =
    arg match{
      case Some( s )  => s.toString
      case None       => default
    }





  private def buildAST( proj: Project ) = {
    // set source file
    val setSourceFileRule = everywheretd{
      rule{
        case b@BNode( ObjectBNode, ch, exp, ObjectDetails( oF, bA, ext, gen, lN, None, fP ) ) => {
          val sourceFile = proj.getSource( b, oF, gen )
          BNode( ObjectBNode, ch, exp,
            ObjectDetails( oF, bA, ext, gen, lN, sourceFile, fP ) )
        }
      }
    }

    val ast = BNode( RootNode, proj.getTopMakefileFolders.flatMap{
      f => proj.findMakefile(f) match{
        case mfs: List[String] if !mfs.isEmpty => mfs.map( processMakefile( _, Some( True() ), proj ) )
        case _ => sys.error("No KBuild Makefile found in: " + f )
      }
    }, None, NoDetails )
    Attribution.initTree( ast )
    val ret = rewrite( setSourceFileRule )( ast )
    Attribution.initTree( ret )
    ret
  }

  private def processMakefile( mf: String, exp: Option[Expression], proj: Project ): BNode = {

    val factory = new ModelFactory(
      BNode( MakefileBNode, List(), exp, MakefileDetails( mf ) ),
      proj )

    info( "=== PreProcessing " + mf )
    // well, not really (for now)

    info( "=== Processing " + mf )

    val input = new ANTLRInputStream( proj getStreamHandle mf )
    val lex = new FuzzyMakefileLexer( input )
    lex setModelFactory factory

    val tokens = new CommonTokenStream( lex )

    // perform the lexing (and a bit more ;)
    tokens toString

    info( "=== PostProcessing " + mf )

    // set source file
    val setSourceFileRule = everywheretd{
      rule{
        case b@BNode( ObjectBNode, ch, exp, ObjectDetails( oF, bA, ext, gen, lN, None, fP ) ) => {
          val sourceFile = proj.getSource( b, oF, gen )
          BNode( ObjectBNode, ch, exp,
            ObjectDetails( oF, bA, ext, gen, lN, sourceFile, fP ) )
        }
      }
    }
    // descend into and process the sub makefiles
    val processMakefilesRule = everywheretd{
      rule{
        case BNode( MakefileBNode, Nil, exp, MakefileDetails( m ) ) if m != mf =>
          processMakefile( m, exp, proj )
      }
    }

    val result = factory.root

    // make sure every MakefileBNode and ObjectBNode has an expression
    assert( collects{
      case b@BNode( MakefileBNode, _, None, _ ) => b
      case b@BNode( ObjectBNode, _, None, _ ) => b
    }(result) isEmpty )

    rewrite( postProcessingRule <*
             sequencerRule <*
//             setSourceFileRule <*
             processMakefilesRule )( result )

  }

  /**
   * condense tree, combine nodes and skip multiple makefile invocations
   */
  val postProcessingRule = everywheretd{
    rule{
      case BNode( t, ch, e, d ) => {
        var skip = Set[BNode]()

        val new_children = ch.flatMap( _ match{

          case b@BNode( MakefileBNode, children, Some(exp), MakefileDetails(mf) ) => {
            if( skip contains b )
              Nil
            else{
              val all = ch.filter( _ match{
                case BNode(_,_,_, MakefileDetails(m) ) if m==mf => true
                case _ => false
              })
              val new_exp = all.map( _.exp.get ).reduceLeft[Expression]( _ | _ )
              skip ++= all

              BNode( MakefileBNode, children, Some( new_exp ), MakefileDetails(mf) ) :: Nil
            }
          }

          case b@BNode( ObjectBNode, children, Some(exp), od@ObjectDetails( of, _, _, _, _, _, _ ) ) => {
            if( skip contains b )
              Nil
            else{
              val all = ch.filter( _ match{
                case BNode(_,_,_, ObjectDetails( o, _, _, _, _, _, _ ) ) if o==of => true
                case _ => false
              })
              val new_exp = all.map( _.exp.get ).reduceLeft[Expression]( _ | _ )
              skip ++= all

              BNode( ObjectBNode, children, Some( new_exp ), od ) :: Nil
            }
          }

          case b@BNode( TempCompositeListBNode, children, expression, tcld@TempCompositeListDetails( ln, sf ) )
            if (sf==None||sf.get=="y") => {

              if( skip contains b )
                Nil
              else{
                val all = ch.filter( _ match{
                  case BNode(_,_,_, TempCompositeListDetails( l, s )) if (l==ln && (s==None||s.get=="y") ) => true
                  case _ => false
                })
                val newTCLchildren = children ::: all.tail.flatMap( _.subnodes )
                skip ++= all
                
                BNode( TempCompositeListBNode, newTCLchildren, expression, tcld ) :: Nil
              }
          }

          case b => b :: Nil
        })

        BNode( t, new_children, e, d )
      }
    }
  }

}
