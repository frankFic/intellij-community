// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics test() {
    return Stream.generate(() -> 10).flatMapToInt(x -> IntStream.range(0, x)).limit(33).summaryStatist<caret>ics();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}