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

import gsd.buildanalysis.linux.model.{TreeHelper, MakefileDetails, BNode}
import java.io.{FileInputStream, InputStream, File}

class Project( val basedir: String, val topFolders: List[String] ) extends TreeHelper {

  val makefileNames = "Kbuild" :: "Kbuild.src" :: "Makefile" :: Nil

  def getTopMakefileFolders: List[String] = topFolders 


  def getStreamHandle( relativePathToMakefile: String ): InputStream = {
      new FileInputStream( basedir + "/" + relativePathToMakefile )
  }


    def lookupSubMakefile( currentMakefile: String, relativePath: String): List[String] = {
        val currentFolder = currentMakefile.substring( 0, Math.max(0,currentMakefile.lastIndexOf('/')) )
        val newPath = basedir + "/" + currentFolder + "/" + relativePath

        findMakefile( currentFolder + "/" + relativePath ) match{
            case m: List[String] if !m.isEmpty =>  m
            case _ => sys.error( "Neither a KBuild nor a Makefile exists in folder " + newPath )
        }

    }

     def findMakefile( folder: String ): List[String] ={
           val _folder = new File( basedir + "/" + folder )
           if (!_folder.exists()) return Nil
           if (_folder.isFile) return folder :: Nil
           assert(_folder.isDirectory, folder+" is not a file or directory")

           for( mf <- makefileNames ){
              val m = new File( basedir + "/" + folder + "/" + mf )
              if( m exists )
                  return folder + ( if( folder endsWith "/" ) "" else "/" ) + mf :: Nil
           }
           Nil
     }



    /**
     * Lookup source file of object node
     */
    def getSource( b: BNode, oF: String, gen: Boolean ): Option[String] ={

        val mf = b->mfScope match{
            case BNode(_,_,_,MakefileDetails(m)) => m
            case _ => sys.error( "No Makefile node!" )
        }

        val currentFolder = if( oF startsWith "/" )
            "" // absolute object path
        else
            mf.substring( 0, Math.max(0, mf lastIndexOf '/' ) )

        // check that source file paths don't start with one or more "/"
        def sanitize( f: String ): String =
            if( f startsWith "/" ) sanitize(f substring 1) else f

        val cPath = currentFolder + "/" + oF + ".c"
        val c = new File( basedir + "/" + cPath )
        if( c.exists || gen ) // safe assumption, since no assembler files are generated
            Some( sanitize( cPath ) )
        else{
            // check for assembler source
            val asmPath = currentFolder + "/" + oF + ".S"
            val a = new File( basedir + "/" + asmPath )
            if( a exists )
                Some( sanitize( asmPath ) )
            else
                None
        }

    }

}
