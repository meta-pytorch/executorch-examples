package com.mathpal.app.gamification

import com.mathpal.app.data.model.Difficulty
import com.mathpal.app.data.model.GradeLevel
import com.mathpal.app.data.model.Problem
import com.mathpal.app.data.model.ProblemCategory
import kotlin.math.abs

object ProblemBank {

    val DAILY_PROBLEMS: List<Problem> = listOf(

        // ========== GRADE 4: Simple arithmetic, basic shapes ==========
        Problem("g4_01", ProblemCategory.ARITHMETIC, Difficulty.FOUNDATION,
            "Emma has 24 stickers and gets 18 more. How many stickers does she have?",
            42.0, "Add 24 + 18.", GradeLevel.GRADE_4),
        Problem("g4_02", ProblemCategory.ARITHMETIC, Difficulty.FOUNDATION,
            "A baker makes 96 cookies and puts 8 in each bag. How many bags does he fill?",
            12.0, "Divide 96 by 8.", GradeLevel.GRADE_4),
        Problem("g4_03", ProblemCategory.ARITHMETIC, Difficulty.FOUNDATION,
            "There are 7 rows of chairs with 9 chairs in each row. How many chairs total?",
            63.0, "Multiply 7 × 9.", GradeLevel.GRADE_4),
        Problem("g4_04", ProblemCategory.GEOMETRY, Difficulty.FOUNDATION,
            "A rectangle is 8 cm long and 5 cm wide. What is its perimeter?",
            26.0, "Perimeter = 2 × (8 + 5).", GradeLevel.GRADE_4),
        Problem("g4_05", ProblemCategory.ARITHMETIC, Difficulty.FOUNDATION,
            "Sam has 50 marbles. He gives 12 to Tom and 8 to Lisa. How many does he have left?",
            30.0, "50 - 12 - 8.", GradeLevel.GRADE_4),
        Problem("g4_06", ProblemCategory.FRACTIONS, Difficulty.FOUNDATION,
            "A pizza is cut into 8 equal slices. You eat 3 slices. What fraction is left? Express as X/8.",
            0.625, "8 - 3 = 5 slices left, so 5/8.", GradeLevel.GRADE_4),
        Problem("g4_07", ProblemCategory.GEOMETRY, Difficulty.FOUNDATION,
            "A square has sides of 6 cm. What is its area in square cm?",
            36.0, "Area = 6 × 6.", GradeLevel.GRADE_4),
        Problem("g4_08", ProblemCategory.ARITHMETIC, Difficulty.FOUNDATION,
            "A toy costs 7 dollars. How much do 6 toys cost?",
            42.0, "Multiply 7 × 6.", GradeLevel.GRADE_4),

        // ========== GRADE 5: Multi-step, decimals, area ==========
        Problem("g5_01", ProblemCategory.DECIMALS_PERCENT, Difficulty.FOUNDATION,
            "A book costs 12.50 dollars and a pen costs 3.75 dollars. How much do they cost together?",
            16.25, "Add 12.50 + 3.75.", GradeLevel.GRADE_5),
        Problem("g5_02", ProblemCategory.GEOMETRY, Difficulty.FOUNDATION,
            "A triangle has a base of 10 cm and height of 6 cm. What is its area?",
            30.0, "Area = (1/2) × 10 × 6.", GradeLevel.GRADE_5),
        Problem("g5_03", ProblemCategory.ARITHMETIC, Difficulty.INTERMEDIATE,
            "A school has 480 students in 12 classrooms. Each classroom has desks in rows of 8. How many rows per classroom?",
            5.0, "480/12 = 40 per classroom. 40/8 = 5 rows.", GradeLevel.GRADE_5),
        Problem("g5_04", ProblemCategory.DECIMALS_PERCENT, Difficulty.FOUNDATION,
            "A runner completes a lap in 2.5 minutes. How long for 6 laps?",
            15.0, "2.5 × 6.", GradeLevel.GRADE_5),
        Problem("g5_05", ProblemCategory.FRACTIONS, Difficulty.INTERMEDIATE,
            "Maria has 3/4 of a yard of ribbon. She uses 1/4. How much is left? Give answer as a fraction.",
            0.5, "3/4 - 1/4 = 2/4 = 1/2.", GradeLevel.GRADE_5),
        Problem("g5_06", ProblemCategory.GEOMETRY, Difficulty.FOUNDATION,
            "A room is 4 meters long and 3 meters wide. How many 1-meter square tiles are needed to cover the floor?",
            12.0, "Area = 4 × 3 = 12.", GradeLevel.GRADE_5),
        Problem("g5_07", ProblemCategory.ARITHMETIC, Difficulty.INTERMEDIATE,
            "A farmer has 3 fields. The first has 45 trees, the second has 38, and the third has 52. He plants 15 more in each field. How many trees total?",
            180.0, "(45+38+52) + (15×3) = 135 + 45.", GradeLevel.GRADE_5),
        Problem("g5_08", ProblemCategory.DECIMALS_PERCENT, Difficulty.FOUNDATION,
            "You buy 3 items at 4.99 dollars each. How much do you pay in total?",
            14.97, "3 × 4.99.", GradeLevel.GRADE_5),

        // ========== GRADE 6: Ratios, percentages, intro algebra ==========
        Problem("g6_01", ProblemCategory.RATIOS, Difficulty.INTERMEDIATE,
            "The ratio of cats to dogs at a shelter is 3:5. If there are 15 cats, how many dogs are there?",
            25.0, "3 parts = 15, so 1 part = 5. Dogs = 5 × 5.", GradeLevel.GRADE_6),
        Problem("g6_02", ProblemCategory.DECIMALS_PERCENT, Difficulty.INTERMEDIATE,
            "A shirt costs 40 dollars and is 20% off. What is the sale price?",
            32.0, "20% of 40 = 8. Sale price = 40 - 8.", GradeLevel.GRADE_6),
        Problem("g6_03", ProblemCategory.LINEAR_EQUATIONS, Difficulty.INTERMEDIATE,
            "Solve for x: x + 15 = 42",
            27.0, "Subtract 15 from both sides.", GradeLevel.GRADE_6),
        Problem("g6_04", ProblemCategory.GEOMETRY, Difficulty.INTERMEDIATE,
            "A rectangular garden is 12 feet long and 9 feet wide. You want to put a fence around it. How many feet of fence do you need?",
            42.0, "Perimeter = 2 × (12 + 9).", GradeLevel.GRADE_6),
        Problem("g6_05", ProblemCategory.RATIOS, Difficulty.INTERMEDIATE,
            "A map scale is 1 inch = 25 miles. Two cities are 3 inches apart. What is the real distance?",
            75.0, "3 × 25.", GradeLevel.GRADE_6),
        Problem("g6_06", ProblemCategory.DECIMALS_PERCENT, Difficulty.INTERMEDIATE,
            "You scored 36 out of 45 on a test. What is your percentage score?",
            80.0, "(36/45) × 100.", GradeLevel.GRADE_6),
        Problem("g6_07", ProblemCategory.LINEAR_EQUATIONS, Difficulty.INTERMEDIATE,
            "Solve for x: 3x = 27",
            9.0, "Divide both sides by 3.", GradeLevel.GRADE_6),
        Problem("g6_08", ProblemCategory.RATIOS, Difficulty.INTERMEDIATE,
            "A recipe for 4 people uses 6 cups of flour. How many cups for 10 people?",
            15.0, "6/4 × 10 = 15.", GradeLevel.GRADE_6),

        // ========== GRADE 7: Proportions, circles, probability ==========
        Problem("g7_01", ProblemCategory.LINEAR_EQUATIONS, Difficulty.INTERMEDIATE,
            "Solve for x: 3x - 7 = 14",
            7.0, "Add 7, then divide by 3.", GradeLevel.GRADE_7),
        Problem("g7_02", ProblemCategory.DECIMALS_PERCENT, Difficulty.INTERMEDIATE,
            "A bicycle costs 250 dollars. It is marked down 15%. What is the sale price?",
            212.5, "15% of 250 = 37.50. Sale = 250 - 37.50.", GradeLevel.GRADE_7),
        Problem("g7_03", ProblemCategory.GEOMETRY, Difficulty.INTERMEDIATE,
            "A circle has a radius of 7 cm. What is its area? Use pi = 3.14.",
            153.86, "Area = 3.14 × 7 × 7.", GradeLevel.GRADE_7),
        Problem("g7_04", ProblemCategory.PROBABILITY, Difficulty.INTERMEDIATE,
            "A bag has 4 red, 6 blue, and 5 green marbles. You pick one at random. What is the probability it is blue? Give as a fraction.",
            0.4, "P(blue) = 6/15 = 2/5.", GradeLevel.GRADE_7),
        Problem("g7_05", ProblemCategory.RATIOS, Difficulty.INTERMEDIATE,
            "If 5 shirts cost 85 dollars, how much do 8 shirts cost?",
            136.0, "85/5 = 17 per shirt. 17 × 8.", GradeLevel.GRADE_7),
        Problem("g7_06", ProblemCategory.GEOMETRY, Difficulty.INTERMEDIATE,
            "What is the circumference of a circle with diameter 10 cm? Use pi = 3.14.",
            31.4, "C = pi × d = 3.14 × 10.", GradeLevel.GRADE_7),
        Problem("g7_07", ProblemCategory.LINEAR_EQUATIONS, Difficulty.INTERMEDIATE,
            "A gym charges 25 dollars signup fee plus 10 dollars per month. You have 95 dollars. How many months can you afford?",
            7.0, "95 - 25 = 70. 70/10 = 7 months.", GradeLevel.GRADE_7),
        Problem("g7_08", ProblemCategory.PROBABILITY, Difficulty.INTERMEDIATE,
            "You roll a fair die. What is the probability of rolling a number greater than 4?",
            0.3333, "P = 2/6 = 1/3 (rolling 5 or 6).", GradeLevel.GRADE_7),

        // ========== GRADE 8: Multi-step, systems, advanced probability ==========
        Problem("g8_01", ProblemCategory.LINEAR_EQUATIONS, Difficulty.CHALLENGE,
            "Solve for x: 2(3x - 4) + 5 = 4x + 11",
            7.0, "Distribute: 6x - 8 + 5 = 4x + 11. Then 2x = 14.", GradeLevel.GRADE_8),
        Problem("g8_02", ProblemCategory.PROBABILITY, Difficulty.CHALLENGE,
            "You flip a coin 4 times. What is the probability of getting exactly 3 heads? Give as a fraction.",
            0.25, "C(4,3) × (1/2)^4 = 4/16 = 1/4.", GradeLevel.GRADE_8),
        Problem("g8_03", ProblemCategory.GEOMETRY, Difficulty.CHALLENGE,
            "A cylinder has radius 5 cm and height 12 cm. What is its volume? Use pi = 3.14.",
            942.0, "V = 3.14 × 5² × 12.", GradeLevel.GRADE_8),
        Problem("g8_04", ProblemCategory.MULTI_STEP, Difficulty.CHALLENGE,
            "A store has buy-2-get-1-free on 15 dollar shirts. You need 7 shirts and have a 10% off coupon. How much do you pay?",
            67.5, "Pay for 5 shirts: 75. After 10% off: 67.50.", GradeLevel.GRADE_8),
        Problem("g8_05", ProblemCategory.RATE_SPEED, Difficulty.CHALLENGE,
            "Train A leaves at 60 mph. Train B leaves 1 hour later at 80 mph same direction. How many hours until B catches A?",
            3.0, "80t = 60(t+1). Solve: t = 3.", GradeLevel.GRADE_8),
        Problem("g8_06", ProblemCategory.PROBABILITY, Difficulty.CHALLENGE,
            "A bag has 5 red and 3 blue balls. Draw 2 without replacement. What is the probability both are red? Give as a fraction.",
            0.3571, "P = (5/8) × (4/7) = 20/56 = 5/14.", GradeLevel.GRADE_8),
        Problem("g8_07", ProblemCategory.DECIMALS_PERCENT, Difficulty.CHALLENGE,
            "You invest 1000 dollars at 5% annual compound interest. How much after 3 years?",
            1157.63, "A = 1000 × (1.05)^3.", GradeLevel.GRADE_8),
        Problem("g8_08", ProblemCategory.MULTI_STEP, Difficulty.CHALLENGE,
            "A class has 30 students. 60% are girls. 25% of girls and 50% of boys play sports. How many play sports total?",
            10.5, "Girls=18, boys=12. Sports: 18×0.25 + 12×0.50.", GradeLevel.GRADE_8),
    )

    fun getProblemsForGrade(grade: GradeLevel): List<Problem> {
        return DAILY_PROBLEMS.filter { it.gradeLevel == grade }
    }

    fun getDailyChallenge(date: String): Problem {
        val challenges = DAILY_PROBLEMS.filter { it.difficulty == Difficulty.CHALLENGE }
        val index = abs(date.hashCode()) % challenges.size
        return challenges[index]
    }

    fun getProblemsForTopic(category: ProblemCategory, count: Int = 5): List<Problem> {
        return DAILY_PROBLEMS.filter { it.category == category }.shuffled().take(count)
    }

    fun getDailyProblems(date: String, grade: GradeLevel = GradeLevel.GRADE_8): List<Problem> {
        val gradeProblems = getProblemsForGrade(grade)
        val seed = abs(date.hashCode())
        return gradeProblems.shuffled(kotlin.random.Random(seed)).take(5)
    }
}
