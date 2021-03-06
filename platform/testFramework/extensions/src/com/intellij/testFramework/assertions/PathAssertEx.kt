/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework.assertions

import com.intellij.util.io.readText
import com.intellij.util.io.size
import org.assertj.core.api.PathAssert
import org.assertj.core.internal.ComparatorBasedComparisonStrategy
import org.assertj.core.internal.Iterables
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.*

class PathAssertEx(actual: Path?) : PathAssert(actual) {
  override fun doesNotExist(): PathAssert {
    isNotNull

    if (Files.exists(actual, LinkOption.NOFOLLOW_LINKS)) {
      var error = "Expecting path:\n\t${actual}\nnot to exist"
      if (actual.size() < 16 * 1024) {
        error += ", content:\n\n${actual.readText()}\n"
      }
      failWithMessage(error)
    }

    return this
  }

  fun hasChildren(vararg names: String) {
    paths.assertIsDirectory(info, actual)

    Iterables(ComparatorBasedComparisonStrategy(
      Comparator<Any> { o1, o2 ->
        if (o1 is Path && o2 is Path) {
          o1.compareTo(o2)
        }
        else if (o1 is String && o2 is String) {
          o1.compareTo(o2)
        }
        else {
          if ((o1 as Path).endsWith(o2 as String)) 0 else -1
        }
      }
    )).assertContainsOnly(info, Files.newDirectoryStream(actual).use { it.toList() }, names)
  }
}