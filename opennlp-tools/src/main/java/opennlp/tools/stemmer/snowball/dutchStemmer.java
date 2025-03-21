// CHECKSTYLE:OFF
/*

Copyright (c) 2001, Dr Martin Porter
Copyright (c) 2002, Richard Boulton
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
    * this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
    * notice, this list of conditions and the following disclaimer in the
    * documentation and/or other materials provided with the distribution.
    * Neither the name of the copyright holders nor the names of its contributors
    * may be used to endorse or promote products derived from this software
    * without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

// Generated by Snowball (build from 9a22f0d3f44cda36677829328fe2642750114d57)
package opennlp.tools.stemmer.snowball;


/**
 * This class implements the stemming algorithm defined by a snowball script.
 * <p>
 * Generated by Snowball (build from 9a22f0d3f44cda36677829328fe2642750114d57) - <a href="https://github.com/snowballstem/snowball">https://github.com/snowballstem/snowball</a>
 * </p>
 */
@SuppressWarnings("unused")
public class dutchStemmer extends AbstractSnowballStemmer {

  private static final long serialVersionUID = 1L;

  private final static Among[] a_0 = {
      new Among("", -1, 6),
      new Among("\u00E1", 0, 1),
      new Among("\u00E4", 0, 1),
      new Among("\u00E9", 0, 2),
      new Among("\u00EB", 0, 2),
      new Among("\u00ED", 0, 3),
      new Among("\u00EF", 0, 3),
      new Among("\u00F3", 0, 4),
      new Among("\u00F6", 0, 4),
      new Among("\u00FA", 0, 5),
      new Among("\u00FC", 0, 5)
  };

  private final static Among[] a_1 = {
      new Among("", -1, 3),
      new Among("I", 0, 2),
      new Among("Y", 0, 1)
  };

  private final static Among[] a_2 = {
      new Among("dd", -1, -1),
      new Among("kk", -1, -1),
      new Among("tt", -1, -1)
  };

  private final static Among[] a_3 = {
      new Among("ene", -1, 2),
      new Among("se", -1, 3),
      new Among("en", -1, 2),
      new Among("heden", 2, 1),
      new Among("s", -1, 3)
  };

  private final static Among[] a_4 = {
      new Among("end", -1, 1),
      new Among("ig", -1, 2),
      new Among("ing", -1, 1),
      new Among("lijk", -1, 3),
      new Among("baar", -1, 4),
      new Among("bar", -1, 5)
  };

  private final static Among[] a_5 = {
      new Among("aa", -1, -1),
      new Among("ee", -1, -1),
      new Among("oo", -1, -1),
      new Among("uu", -1, -1)
  };

  private static final char[] g_v = {17, 65, 16, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 128};

  private static final char[] g_v_I = {1, 0, 0, 17, 65, 16, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 128};

  private static final char[] g_v_j = {17, 67, 16, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 128};

  private int I_p2;
  private int I_p1;
  private boolean B_e_found;


  private boolean r_prelude() {
    int among_var;
    int v_1 = cursor;
    while (true) {
      int v_2 = cursor;
      lab0:
      {
        bra = cursor;
        among_var = find_among(a_0);
        ket = cursor;
        switch (among_var) {
          case 1:
            slice_from("a");
            break;
          case 2:
            slice_from("e");
            break;
          case 3:
            slice_from("i");
            break;
          case 4:
            slice_from("o");
            break;
          case 5:
            slice_from("u");
            break;
          case 6:
            if (cursor >= limit) {
              break lab0;
            }
            cursor++;
            break;
        }
        continue;
      }
      cursor = v_2;
      break;
    }
    cursor = v_1;
    int v_3 = cursor;
    lab1:
    {
      bra = cursor;
      if (!(eq_s("y"))) {
        cursor = v_3;
        break lab1;
      }
      ket = cursor;
      slice_from("Y");
    }
    while (true) {
      int v_4 = cursor;
      lab2:
      {
        golab3:
        while (true) {
          int v_5 = cursor;
          lab4:
          {
            if (!(in_grouping(g_v, 97, 232))) {
              break lab4;
            }
            bra = cursor;
            lab5:
            {
              int v_6 = cursor;
              lab6:
              {
                if (!(eq_s("i"))) {
                  break lab6;
                }
                ket = cursor;
                if (!(in_grouping(g_v, 97, 232))) {
                  break lab6;
                }
                slice_from("I");
                break lab5;
              }
              cursor = v_6;
              if (!(eq_s("y"))) {
                break lab4;
              }
              ket = cursor;
              slice_from("Y");
            }
            cursor = v_5;
            break golab3;
          }
          cursor = v_5;
          if (cursor >= limit) {
            break lab2;
          }
          cursor++;
        }
        continue;
      }
      cursor = v_4;
      break;
    }
    return true;
  }

  private boolean r_mark_regions() {
    I_p1 = limit;
    I_p2 = limit;
    golab0:
    while (true) {
      lab1:
      {
        if (!(in_grouping(g_v, 97, 232))) {
          break lab1;
        }
        break golab0;
      }
      if (cursor >= limit) {
        return false;
      }
      cursor++;
    }
    golab2:
    while (true) {
      lab3:
      {
        if (!(out_grouping(g_v, 97, 232))) {
          break lab3;
        }
        break golab2;
      }
      if (cursor >= limit) {
        return false;
      }
      cursor++;
    }
    I_p1 = cursor;
    lab4:
    {
      if (I_p1 >= 3) {
        break lab4;
      }
      I_p1 = 3;
    }
    golab5:
    while (true) {
      lab6:
      {
        if (!(in_grouping(g_v, 97, 232))) {
          break lab6;
        }
        break golab5;
      }
      if (cursor >= limit) {
        return false;
      }
      cursor++;
    }
    golab7:
    while (true) {
      lab8:
      {
        if (!(out_grouping(g_v, 97, 232))) {
          break lab8;
        }
        break golab7;
      }
      if (cursor >= limit) {
        return false;
      }
      cursor++;
    }
    I_p2 = cursor;
    return true;
  }

  private boolean r_postlude() {
    int among_var;
    while (true) {
      int v_1 = cursor;
      lab0:
      {
        bra = cursor;
        among_var = find_among(a_1);
        ket = cursor;
        switch (among_var) {
          case 1:
            slice_from("y");
            break;
          case 2:
            slice_from("i");
            break;
          case 3:
            if (cursor >= limit) {
              break lab0;
            }
            cursor++;
            break;
        }
        continue;
      }
      cursor = v_1;
      break;
    }
    return true;
  }

  private boolean r_R1() {
    return I_p1 <= cursor;
  }

  private boolean r_R2() {
    return I_p2 <= cursor;
  }

  private boolean r_undouble() {
    int v_1 = limit - cursor;
    if (find_among_b(a_2) == 0) {
      return false;
    }
    cursor = limit - v_1;
    ket = cursor;
    if (cursor <= limit_backward) {
      return false;
    }
    cursor--;
    bra = cursor;
    slice_del();
    return true;
  }

  private boolean r_e_ending() {
    B_e_found = false;
    ket = cursor;
    if (!(eq_s_b("e"))) {
      return false;
    }
    bra = cursor;
    if (!r_R1()) {
      return false;
    }
    int v_1 = limit - cursor;
    if (!(out_grouping_b(g_v, 97, 232))) {
      return false;
    }
    cursor = limit - v_1;
    slice_del();
    B_e_found = true;
    if (!r_undouble()) {
      return false;
    }
    return true;
  }

  private boolean r_en_ending() {
    if (!r_R1()) {
      return false;
    }
    int v_1 = limit - cursor;
    if (!(out_grouping_b(g_v, 97, 232))) {
      return false;
    }
    cursor = limit - v_1;
    {
      int v_2 = limit - cursor;
      lab0:
      {
        if (!(eq_s_b("gem"))) {
          break lab0;
        }
        return false;
      }
      cursor = limit - v_2;
    }
    slice_del();
    if (!r_undouble()) {
      return false;
    }
    return true;
  }

  private boolean r_standard_suffix() {
    int among_var;
    int v_1 = limit - cursor;
    lab0:
    {
      ket = cursor;
      among_var = find_among_b(a_3);
      if (among_var == 0) {
        break lab0;
      }
      bra = cursor;
      switch (among_var) {
        case 1:
          if (!r_R1()) {
            break lab0;
          }
          slice_from("heid");
          break;
        case 2:
          if (!r_en_ending()) {
            break lab0;
          }
          break;
        case 3:
          if (!r_R1()) {
            break lab0;
          }
          if (!(out_grouping_b(g_v_j, 97, 232))) {
            break lab0;
          }
          slice_del();
          break;
      }
    }
    cursor = limit - v_1;
    int v_2 = limit - cursor;
    r_e_ending();
    cursor = limit - v_2;
    int v_3 = limit - cursor;
    lab1:
    {
      ket = cursor;
      if (!(eq_s_b("heid"))) {
        break lab1;
      }
      bra = cursor;
      if (!r_R2()) {
        break lab1;
      }
      {
        int v_4 = limit - cursor;
        lab2:
        {
          if (!(eq_s_b("c"))) {
            break lab2;
          }
          break lab1;
        }
        cursor = limit - v_4;
      }
      slice_del();
      ket = cursor;
      if (!(eq_s_b("en"))) {
        break lab1;
      }
      bra = cursor;
      if (!r_en_ending()) {
        break lab1;
      }
    }
    cursor = limit - v_3;
    int v_5 = limit - cursor;
    lab3:
    {
      ket = cursor;
      among_var = find_among_b(a_4);
      if (among_var == 0) {
        break lab3;
      }
      bra = cursor;
      switch (among_var) {
        case 1:
          if (!r_R2()) {
            break lab3;
          }
          slice_del();
          lab4:
          {
            int v_6 = limit - cursor;
            lab5:
            {
              ket = cursor;
              if (!(eq_s_b("ig"))) {
                break lab5;
              }
              bra = cursor;
              if (!r_R2()) {
                break lab5;
              }
              {
                int v_7 = limit - cursor;
                lab6:
                {
                  if (!(eq_s_b("e"))) {
                    break lab6;
                  }
                  break lab5;
                }
                cursor = limit - v_7;
              }
              slice_del();
              break lab4;
            }
            cursor = limit - v_6;
            if (!r_undouble()) {
              break lab3;
            }
          }
          break;
        case 2:
          if (!r_R2()) {
            break lab3;
          }
        {
          int v_8 = limit - cursor;
          lab7:
          {
            if (!(eq_s_b("e"))) {
              break lab7;
            }
            break lab3;
          }
          cursor = limit - v_8;
        }
        slice_del();
        break;
        case 3:
          if (!r_R2()) {
            break lab3;
          }
          slice_del();
          if (!r_e_ending()) {
            break lab3;
          }
          break;
        case 4:
          if (!r_R2()) {
            break lab3;
          }
          slice_del();
          break;
        case 5:
          if (!r_R2()) {
            break lab3;
          }
          if (!(B_e_found)) {
            break lab3;
          }
          slice_del();
          break;
      }
    }
    cursor = limit - v_5;
    int v_9 = limit - cursor;
    lab8:
    {
      if (!(out_grouping_b(g_v_I, 73, 232))) {
        break lab8;
      }
      int v_10 = limit - cursor;
      if (find_among_b(a_5) == 0) {
        break lab8;
      }
      if (!(out_grouping_b(g_v, 97, 232))) {
        break lab8;
      }
      cursor = limit - v_10;
      ket = cursor;
      if (cursor <= limit_backward) {
        break lab8;
      }
      cursor--;
      bra = cursor;
      slice_del();
    }
    cursor = limit - v_9;
    return true;
  }

  @Override
  public boolean stem() {
    int v_1 = cursor;
    r_prelude();
    cursor = v_1;
    int v_2 = cursor;
    r_mark_regions();
    cursor = v_2;
    limit_backward = cursor;
    cursor = limit;
    r_standard_suffix();
    cursor = limit_backward;
    int v_4 = cursor;
    r_postlude();
    cursor = v_4;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof dutchStemmer;
  }

  @Override
  public int hashCode() {
    return dutchStemmer.class.getName().hashCode();
  }


}

