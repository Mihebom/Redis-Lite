package tests;

import java.util.ArrayList;
import java.util.LinkedList;

public class TestClass {

    public static void main(String[] args) {

//        String s1 = "foo";
//
//        String s2 = "foa";
//
//        int str1 = s1.length();
//
//        int str2 = s2.length();
//
//        int[][] lcs = new int[str1 + 1][str2 + 1];
//
//        for(int i = 1; i <= str1; i++){
//            for(int j = 1; j <= str2; j++){
//
//                if(s1.charAt(i - 1) == s2.charAt(j - 1)){
//
//                    lcs[i][j] = lcs[i-1][j-1] + 1;
//                } else {
//                    lcs[i][j] = Math.max(lcs[i-1][j],lcs[i][j-1]);
//                }
//
//            }
//        }

//
//        System.out.println(lcs[str1][str2]);

        System.out.println((2 == 3 || 1 == 4));

//        int count = 3;
//
//        ArrayList<String> elems = new ArrayList<>();
//
//        LinkedList<String> test = new LinkedList<>();
//        test.add("a");
//        test.add("b");
//        test.add("c");
//        test.add("d");
//
//
//        LinkedList<String> test1 = new LinkedList<>();
//
//        for(int i = 0; i < count; i++){
//
//            String temp = test.removeFirst();
//            elems.add(temp);
//        }
//
//        test1.addAll(elems);
//
//        System.out.println("TEST:");
//        test.forEach(System.out::println);
//        System.out.println("TEST1:");
//        test1.forEach(System.out::println);
//        System.out.println("ELEMS:");
//        elems.forEach(System.out::println);
    }


    public int longestCommonSubsequence(String text1, String text2) {

        int s1 = text1.length();

        int s2 = text2.length();

        int[][] lcs = new int[s1 + 1][s2 + 1];

        for(int i = 1; i <= s1; i++){
            for(int j = 1; j <= s2; j++){

                if(text1.charAt(i - 1) == text2.charAt(j - 1)){
                    lcs[i][j] = lcs[i-1][j-1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i-1][j],lcs[i][j-1]);
                }

            }

        }

        return lcs[s1][s2];

    }
}
