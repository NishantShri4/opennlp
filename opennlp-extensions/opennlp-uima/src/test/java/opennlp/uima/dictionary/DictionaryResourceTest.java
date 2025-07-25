/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.uima.dictionary;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;
import opennlp.uima.AbstractTest;
import opennlp.uima.util.CasUtil;

public class DictionaryResourceTest extends AbstractTest {

  private static AnalysisEngine AE;

  @BeforeAll
  public static void beforeClass() throws Exception {
    AE = produceAE("DictionaryNameFinder.xml");
  }

  @AfterAll
  public static void afterClass() {
    AE.destroy(); // is this necessary?
  }

  private static AnalysisEngine produceAE(String descName)
      throws IOException, InvalidXMLException, ResourceInitializationException {
    File descFile = new File(PATH_DESCRIPTORS + "/" + descName);
    XMLInputSource in = new XMLInputSource(descFile);
    ResourceSpecifier specifier = UIMAFramework.getXMLParser()
        .parseResourceSpecifier(in);
    return UIMAFramework.produceAnalysisEngine(specifier);
  }

  @Test
  public void testDictionaryWasLoaded() {

    try {
      final DictionaryResource dic = (DictionaryResource) AE.getResourceManager()
          .getResource("/opennlp.uima.Dictionary");
      final Dictionary d = dic.getDictionary();
      Assertions.assertNotNull(d);
      Assertions.assertEquals(6, d.asStringSet().size(),
              "There should be six entries in the dictionary");
      Assertions.assertTrue(d.contains(new StringList("London")),
          "London should be in the dictionary");
    } catch (Exception e) {
      Assertions.fail("Dictionary was not loaded.");
    }

  }

  @Test
  public void testDictionaryNameFinder() {

    Set<String> expectedLocations = new HashSet<>();
    Collections.addAll(expectedLocations, "London", "Stockholm", "Copenhagen",
        "New York");

    try {
      CAS cas = AE.newCAS();
      CasUtil.deserializeXmiCAS(cas, DictionaryResourceTest.class
          .getResourceAsStream("/cas/dictionary-test.xmi"));
      AE.process(cas);
      Type locationType = cas.getTypeSystem().getType("opennlp.uima.Location");
      FSIterator<AnnotationFS> locationIterator = cas
          .getAnnotationIndex(locationType).iterator();

      while (locationIterator.isValid()) {
        AnnotationFS annotationFS = locationIterator.get();
        Assertions.assertTrue(expectedLocations.contains(annotationFS.getCoveredText()));
        expectedLocations.remove(annotationFS.getCoveredText());
        locationIterator.moveToNext();
      }
      Assertions.assertEquals(0, expectedLocations.size());
    } catch (Exception e) {
      Assertions.fail(e.getLocalizedMessage());
    }

  }

}
