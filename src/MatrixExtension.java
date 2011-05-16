/* Copyright (c) 2009-2011: Forrest Stonedahl and Uri Wilensky.
* 
* Thanks to Charles Staelin for his contributions (especially the 
* regression & forecasting primitives)!
* 
* See the README.TXT for more information.
*/
package org.nlogo.extensions.matrix;

import org.nlogo.api.CompilerException;
import org.nlogo.api.LogoException;
import org.nlogo.api.ExtensionException;
import org.nlogo.api.Argument;
import org.nlogo.api.Syntax;
import org.nlogo.api.Context;
import org.nlogo.api.LogoList;
import org.nlogo.api.LogoListBuilder;
import org.nlogo.api.DefaultReporter;
import org.nlogo.api.DefaultCommand;

public class MatrixExtension
    extends org.nlogo.api.DefaultClassManager {

  public java.util.List<String> additionalJars() {
    java.util.List<String> list = new java.util.ArrayList<String>();
    list.add("Jama-1.0.2.jar");
    return list;
  }

  // the WeakHashMap here may seem a bit odd, but it is apparently the easiest way to handle things
  // for explanation, see the comment in ArrayExtension.java in the Array extension.
  private static final java.util.WeakHashMap<LogoMatrix, Long> matrices = new java.util.WeakHashMap<LogoMatrix, Long>();
  private static long next = 0;

  private static class LogoMatrix
      // new NetLogo data types defined by extensions must implement
      // this interface
      implements org.nlogo.api.ExtensionObject {
    // NOTE: Because the Jama.Matrix does not support resizing/reshaping
    //       the underlying array, it turned out to be simpler to
    //       store the matrix in a member field, rather than have LogoMatrix
    //       be a subclass of Jama.Matrix.

    Jama.Matrix matrix = null;
    private final long id;

    /**
     * should be used only when doing importWorld, and
     * we only have a reference to the Object, where the data
     * will be defined later.
     */
    LogoMatrix(long id) {
      matrix = null;
      this.id = id;
      matrices.put(this, id);
      next = StrictMath.max(next, id + 1);
    }

    LogoMatrix(Jama.Matrix matrixData) {
      matrix = matrixData;
      matrices.put(this, next);
      this.id = next;
      next++;
    }

    public void replaceData(double[][] dArray) {
      matrix = new Jama.Matrix(dArray);
    }

    /**
     * This is a very shallow "equals", see recursivelyEqual()
     * for deep equality.
     */
    public boolean equals(Object obj) {
      return this == obj;
    }

    public String dump(boolean readable, boolean exporting, boolean reference) {
      StringBuilder buf = new StringBuilder();
      if (exporting) {
        buf.append(id);
        if (!reference) {
          buf.append(":");
        }
      }
      if (!(reference && exporting)) {
        double[][] dArray = this.matrix.getArray();
        buf.append(" [ ");
        for (int i = 0; i < dArray.length; i++) {
          buf.append("[");
          for (int j = 0; j < dArray[i].length; j++) {
            buf.append(" ");
            buf.append(org.nlogo.api.Dump.number(dArray[i][j]));
          }
          buf.append(" ]");
        }
        buf.append(" ]");
      }
      return buf.toString();
    }

    public String getExtensionName() {
      return "matrix";
    }

    public String getNLTypeName() {
      // since this extension only defines one type, we don't
      // need to give it a name; "matrix:" is enough,
      // "matrix:matrix" would be redundant
      return "";
    }

    public boolean recursivelyEqual(Object o) {
      if (!(o instanceof LogoMatrix)) {
        return false;
      }
      LogoMatrix otherMatrix = (LogoMatrix) o;
      double[][] otherArray = otherMatrix.matrix.getArray();
      return java.util.Arrays.deepEquals(matrix.getArray(), otherArray);
    }
  }

  public void clearAll() {
    matrices.clear();
    next = 0;
  }

  public StringBuilder exportWorld() {
    StringBuilder buffer = new StringBuilder();
    for (LogoMatrix mat : matrices.keySet()) {
      buffer.append(org.nlogo.api.Dump.csv.encode(org.nlogo.api.Dump.extensionObject(mat, true, true, false)) + "\n");
    }
    return buffer;
  }

  public void importWorld(java.util.List<String[]> lines, org.nlogo.api.ExtensionManager reader,
                          org.nlogo.api.ImportErrorHandler handler) {
    for (String[] line : lines) {
      try {
        reader.readFromString(line[0]);
      } catch (CompilerException e) {
        handler.showError("Error importing matrices", e.getMessage(), "This matrix will be ignored");
      }
    }
  }

  public org.nlogo.api.ExtensionObject readExtensionObject(org.nlogo.api.ExtensionManager reader,
                                                           String typeName, String value)
      throws CompilerException, ExtensionException {
    String[] s = value.split(":");
    long id = Long.parseLong(s[0]);
    LogoMatrix mat = getOrCreateMatrixFromId(id);
    if (s.length > 1) {
      LogoList nestedL = (LogoList) reader.readFromString(s[1]);
      double[][] newData = convertNestedLogoListToArray(nestedL);
      mat.replaceData(newData);
    }
    return mat;
  }

  private static double[][] convertNestedLogoListToArray(LogoList nestedLogoList) throws ExtensionException {
    int numRows = nestedLogoList.size();
    if (numRows == 0) {
      throw new ExtensionException("input list was empty");
    }
    int numCols = -1;
    // find out the maximum column size of any of the rows,
    // in case we have a "ragged" right edge, where some rows
    // have more columns than others.
    for (Object obj : nestedLogoList) {
      if (obj instanceof LogoList) {
        LogoList rowList = (LogoList) obj;
        if (numCols == -1) {
          numCols = rowList.size();
        } else if (numCols != rowList.size()) {
          throw new ExtensionException("To convert a nested list into a matrix, all nested lists must be the same length -- e.g. [[1 2 3 4] [1 2 3]] is invalid, because row 1 has one more entry.");
        }
      } else {
        throw new ExtensionException("To convert a nested list into a matrix, there must be exactly two levels of nesting -- e.g. [[1 2 3] [4 5 6]] creates a good 2x3 matrix.");
      }
    }
    if (numCols == 0) {
      throw new ExtensionException("input list contained only empty lists");
    }
    double[][] array = new double[numRows][numCols];
    int row = 0;
    for (Object obj : nestedLogoList) {
      int col = 0;
      LogoList rowList = (LogoList) obj;
      for (Object obj2 : rowList) {
        if (obj2 instanceof Number) {
          array[row][col] = ((Number) obj2).doubleValue();
          col++;
        }
      }
      // pad with zeros if we have a "ragged" right edge
      for (; col < numCols; col++) {
        array[row][col] = 0.0;
      }
      row++;
    }

    return array;
  }

  private static double[][] convertSimpleLogoListToArray(LogoList SimpleLogoList) throws ExtensionException {
    int numRows = 1;
    int numCols = SimpleLogoList.size();

    double[][] array = new double[numRows][numCols];
    int row = 0;
    for (int i = 0; i < numCols; i++) {
      array[row][i] = ((Number) SimpleLogoList.get(i)).doubleValue();
    }

    return array;
  }

  private static LogoList convertArrayToNestedLogoList(double[][] dArray) {
    LogoListBuilder lst = new LogoListBuilder();
    for (int i = 0; i < dArray.length; i++) {
      LogoListBuilder rowLst = new LogoListBuilder();
      for (int j = 0; j < dArray[i].length; j++) {
        rowLst.add(Double.valueOf(dArray[i][j]));
      }
      lst.add(rowLst.toLogoList());
    }
    return lst.toLogoList();
  }

  private static LogoList convertArrayToSimpleLogoList(double[][] dArray) {
    LogoListBuilder lst = new LogoListBuilder();
    for (int i = 0; i < dArray.length; i++) {
      for (int j = 0; j < dArray[i].length; j++) {
        lst.add(Double.valueOf(dArray[i][j]));
      }
    }
    return lst.toLogoList();
  }

  /**
   * Used during import world, to recreate matrices with the
   * correct id numbers, so all the references match up.
   *
   * @param id
   * @return
   */
  private LogoMatrix getOrCreateMatrixFromId(long id) {
    for (LogoMatrix mat : matrices.keySet()) {
      if (mat.id == id) {
        return mat;
      }
    }
    return new LogoMatrix(id);
  }

  ///
  public void load(org.nlogo.api.PrimitiveManager primManager) {

    // matrix:get mat rowI colJ  =>  value at location I,J
    primManager.addPrimitive("get", new Get());
    // matrix:set mat rowI colJ newValue
    primManager.addPrimitive("set", new Set());
    // matrix:set-row mat rowI simpleList
    primManager.addPrimitive("set-row", new SetRow());
    // matrix:swap-rows mat row1 row2
    primManager.addPrimitive("swap-rows", new SwapRows());
    // matrix:set-column mat colI simpleList
    primManager.addPrimitive("set-column", new SetColumn());
    // matrix:swap-columns mat col1 col2
    primManager.addPrimitive("swap-columns", new SwapColumns());
    // matrix:set-and-report mat rowI colJ newValue => matrix object
    primManager.addPrimitive("set-and-report", new SetAndReport());
    // (matrix:dimensions mat) => [numRows,numCols]
    primManager.addPrimitive("dimensions", new Dimensions());
    // matrix:to-row-list mat => [[a11 a12 ...] [a21 a22 ...] [a31 a32 ...] ...]
    primManager.addPrimitive("to-row-list", new ToRowList());
    // matrix:from-row-list nestedList => matrix object
    primManager.addPrimitive("from-row-list", new FromRowList());
    // matrix:to-column-list mat => [[a11 a21 ...] [a12 a22 ...] [a13 a23 ...] ...]
    primManager.addPrimitive("to-column-list", new ToColumnList());
    // matrix:from-column-list nestedList => matrix object
    primManager.addPrimitive("from-column-list", new FromColumnList());

    // matrix:make-constant nRows nCols dValue => matrix object
    primManager.addPrimitive("make-constant", new MakeConstant());
    // matrix:make-identity nSize => matrix object
    primManager.addPrimitive("make-identity", new MakeIdentity());
    // matrix:copy mat => matrix object
    primManager.addPrimitive("copy", new Copy());

    // matrix:pretty-print-text matrix => string containing formatted text
    primManager.addPrimitive("pretty-print-text", new PrettyPrintText());

    // matrix:times-scalar mat factor => matrix object
    primManager.addPrimitive("times-scalar", new TimesScalar());
    // matrix:times mat1 mat2 => matrix object
    primManager.addPrimitive("times", new Times());
    // matrix:times-element-wise mat1 mat2 => matrix object
    primManager.addPrimitive("times-element-wise", new TimesElementWise());
    // matrix:plus-scalar mat value => matrix object
    primManager.addPrimitive("plus-scalar", new PlusScalar());
    // matrix:plus mat1 mat2 => matrix object
    primManager.addPrimitive("plus", new Plus());
    // matrix:det mat => number
    primManager.addPrimitive("det", new Det());
    // matrix:rank mat => number
    primManager.addPrimitive("rank", new Rank());
    // matrix:cond mat => number
    primManager.addPrimitive("cond", new Cond());
    // matrix:trace mat => number
    primManager.addPrimitive("trace", new Trace());

    // matrix:inverse mat => matrix object
    primManager.addPrimitive("inverse", new Inverse());
    // matrix:transpose mat => matrix object
    primManager.addPrimitive("transpose", new Transpose());
    // matrix:submatrix mat r1 c1 r2 c2 => matrix object
    primManager.addPrimitive("submatrix", new Submatrix());
    // matrix:get-row mat r => simple (unnested) list of row elements
    primManager.addPrimitive("get-row", new GetRow());
    // matrix:get-column mat c => simple (unnested) list of column elements
    primManager.addPrimitive("get-column", new GetColumn());

    // matrix:real-eigenvalues mat => list of numbers
    primManager.addPrimitive("real-eigenvalues", new RealEigenvalues());
    // matrix:imaginary-eigenvalues mat => list of numbers
    primManager.addPrimitive("imaginary-eigenvalues", new ImaginaryEigenvalues());
    // matrix:eigenvectors mat => matrix of eigenvectors
    primManager.addPrimitive("eigenvectors", new Eigenvectors());

    // matrix:solve mat1 mat2 => matrix object
    //  (solve for M such that  mat1 * M = mat2)
    // gives least-squares solution, if no perfect solution exists.
    primManager.addPrimitive("solve", new Solve());

    // matrix:linear-forecast simpleList => list of [forecast, constant, slope, R^2]
    primManager.addPrimitive("forecast-linear-growth", new ForecastLinearTrend());

    // matrix:compound-growh-forecast simpleList => list of [forecast, constant, (1+rate), R^2]
    primManager.addPrimitive("forecast-compound-growth", new ForecastCompoundTrend());

    // matrix:exponential-forecast simpleList => list of [forecast, constant, rate, R^2]
    primManager.addPrimitive("forecast-continuous-growth", new ForecastContinuousTrend());

    // matrix:regress nestedList => nestedList of [[a(0) ...a(n)] [R^2 totalSumSquares redidualSumSquares]]
    primManager.addPrimitive("regress", new Regress());

    //Note: The Jama library that we're using can do more than just the functionality
    //      that we've exposed here.  (e.g. LU, Cholesky, SV decomposition, determinants)
    //      Motivated persons could add more primitives to access these functions...

  }

  ///
  // Convenience method, to extract a Matrix object from an Argument.
  // It serves a similar purpose to args[x].getString(), or args[x].getList().
  private static LogoMatrix getMatrixFromArgument(Argument arg)
      throws ExtensionException, LogoException {
    Object obj = arg.get();
    if (!(obj instanceof LogoMatrix)) {
      throw new org.nlogo.api.ExtensionException("not a matrix: "
          + org.nlogo.api.Dump.logoObject(obj));
    }
    return (LogoMatrix) obj;
  }

  public static class Get extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_NUMBER},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int rowIndex = args[1].getIntValue();
      int colIndex = args[2].getIntValue();

      if (rowIndex < 0 || rowIndex >= mat.matrix.getRowDimension()
          || colIndex < 0 || colIndex >= mat.matrix.getColumnDimension()) {
        throw new org.nlogo.api.ExtensionException("(" + rowIndex + "," + colIndex + ") are not valid indices for a matrix with dimensions  "
            + mat.matrix.getRowDimension() + "x" + mat.matrix.getColumnDimension());
      }
      return mat.matrix.get(rowIndex, colIndex);
    }
  }

  public static class Set extends DefaultCommand {

    public Syntax getSyntax() {
      return Syntax.commandSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_WILDCARD});
    }

    public void perform(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int rowIndex = args[1].getIntValue();
      int colIndex = args[2].getIntValue();
      if (rowIndex < 0 || rowIndex >= mat.matrix.getRowDimension()
          || colIndex < 0 || colIndex >= mat.matrix.getColumnDimension()) {
        throw new org.nlogo.api.ExtensionException("(" + rowIndex + "," + colIndex + ") are not valid indices for a matrix with dimensions  "
            + mat.matrix.getRowDimension() + "x" + mat.matrix.getColumnDimension());
      }
      mat.matrix.set(rowIndex, colIndex, args[3].getDoubleValue());
    }
  }

  public static class SetRow extends DefaultCommand {

    public Syntax getSyntax() {
      return Syntax.commandSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_LIST});
    }

    public void perform(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int rowIndex = args[1].getIntValue();
      Jama.Matrix newRow = new Jama.Matrix(convertSimpleLogoListToArray(args[2].getList()));
      int newRowLength = newRow.getColumnDimension();
      if (rowIndex < 0 || rowIndex >= mat.matrix.getRowDimension()) {
        throw new org.nlogo.api.ExtensionException(rowIndex + " is not valid row index for a matrix with dimensions "
            + mat.matrix.getRowDimension() + "x" + mat.matrix.getColumnDimension());
      }
      if (newRowLength != mat.matrix.getColumnDimension()) {
        throw new org.nlogo.api.ExtensionException("The length of the given list (" + newRowLength +
            ") is different from the length of the matrix row (" + mat.matrix.getColumnDimension() + ").");
      }

      mat.matrix.setMatrix(rowIndex, rowIndex, 0, newRowLength - 1, newRow);
    }
  }

  public static class SwapRows extends DefaultCommand {

    public Syntax getSyntax() {
      return Syntax.commandSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_NUMBER});
    }

    public void perform(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int rowIndex1 = args[1].getIntValue();
      int rowIndex2 = args[2].getIntValue();
      int numCols = mat.matrix.getColumnDimension();
      int numRows = mat.matrix.getRowDimension();
      if (rowIndex1 < 0 || rowIndex1 >= numRows) {
        throw new org.nlogo.api.ExtensionException("The first row index, " + rowIndex1
            + ", is not valid for a " + numRows + " x " + numCols + " matrix.");
      }
      if (rowIndex2 < 0 || rowIndex2 >= numRows) {
        throw new org.nlogo.api.ExtensionException("The second row index, " + rowIndex2
            + ", is not valid for a " + numRows + " x " + numCols + " matrix.");
      }

      // Save the row given by rowIndex1 in row1; overwrite that row with the row given by
      // rowIndex2, then overwrite the row given by rowIndex2 with the saved row, row1.
      Jama.Matrix row1 = mat.matrix.getMatrix(rowIndex1, rowIndex1, 0, numCols - 1);
      mat.matrix.setMatrix(rowIndex1, rowIndex1, 0, numCols - 1,
          mat.matrix.getMatrix(rowIndex2, rowIndex2, 0, numCols - 1));
      mat.matrix.setMatrix(rowIndex2, rowIndex2, 0, numCols - 1, row1);
    }
  }

  public static class SetColumn extends DefaultCommand {

    public Syntax getSyntax() {
      return Syntax.commandSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_LIST});
    }

    public void perform(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int colIndex = args[1].getIntValue();
      Jama.Matrix newCol = new Jama.Matrix(convertSimpleLogoListToArray(args[2].getList())).transpose();
      int newColLength = newCol.getRowDimension();
      if (colIndex < 0 || colIndex >= mat.matrix.getColumnDimension()) {
        throw new org.nlogo.api.ExtensionException(colIndex + " is not valid column index for a matrix with dimensions "
            + mat.matrix.getRowDimension() + "x" + mat.matrix.getColumnDimension());
      }
      if (newColLength != mat.matrix.getRowDimension()) {
        throw new org.nlogo.api.ExtensionException("The length of the given list (" + newColLength +
            ") is different from the length of the matrix column (" + mat.matrix.getRowDimension() + ").");
      }

      mat.matrix.setMatrix(0, newColLength - 1, colIndex, colIndex, newCol);
    }
  }

  public static class SwapColumns extends DefaultCommand {

    public Syntax getSyntax() {
      return Syntax.commandSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_NUMBER});
    }

    public void perform(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int colIndex1 = args[1].getIntValue();
      int colIndex2 = args[2].getIntValue();
      int numCols = mat.matrix.getColumnDimension();
      int numRows = mat.matrix.getRowDimension();
      if (colIndex1 < 0 || colIndex1 >= numCols) {
        throw new org.nlogo.api.ExtensionException("The first column index, " + colIndex1
            + ", is not valid for a " + numRows + " x " + numCols + " matrix.");
      }
      if (colIndex2 < 0 || colIndex2 >= numCols) {
        throw new org.nlogo.api.ExtensionException("The second column index, " + colIndex2
            + ", is not valid for a " + numRows + " x " + numCols + " matrix.");
      }

      // Save the column given by colIndex1 in col1; overwrite that column with the column given by
      // colIndex2, then overwrite the column given by colIndex2 with the saved column, col1.
      Jama.Matrix col1 = mat.matrix.getMatrix(0, numRows - 1, colIndex1, colIndex1);
      mat.matrix.setMatrix(0, numRows - 1, colIndex1, colIndex1,
          mat.matrix.getMatrix(0, numRows - 1, colIndex2, colIndex2));
      mat.matrix.setMatrix(0, numRows - 1, colIndex2, colIndex2, col1);
    }
  }

  public static class SetAndReport extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_NUMBER,
          Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int rowIndex = args[1].getIntValue();
      int colIndex = args[2].getIntValue();
      if (rowIndex < 0 || rowIndex >= mat.matrix.getRowDimension()
          || colIndex < 0 || colIndex >= mat.matrix.getColumnDimension()) {
        throw new org.nlogo.api.ExtensionException("(" + rowIndex + "," + colIndex + ") are not valid indices for a matrix with dimensions  "
            + mat.matrix.getRowDimension() + "x" + mat.matrix.getColumnDimension());
      }
      LogoMatrix matcopy = new LogoMatrix(getMatrixFromArgument(args[0]).matrix.copy());
      matcopy.matrix.set(rowIndex, colIndex, args[3].getDoubleValue());
      return matcopy;
    }
  }

  public static class Dimensions extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      LogoListBuilder dims = new LogoListBuilder();
      dims.add((double) mat.matrix.getRowDimension());
      dims.add((double) mat.matrix.getColumnDimension());
      return dims.toLogoList();
    }
  }

  public static class ToRowList extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      return convertArrayToNestedLogoList(getMatrixFromArgument(args[0]).matrix.getArray());
    }
  }

  public static class FromRowList extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_LIST},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      return new LogoMatrix(new Jama.Matrix(convertNestedLogoListToArray(args[0].getList())));
    }
  }

  public static class ToColumnList extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      return convertArrayToNestedLogoList(getMatrixFromArgument(args[0]).matrix.transpose().getArray());
    }
  }

  public static class FromColumnList extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_LIST},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      return new LogoMatrix(new Jama.Matrix(convertNestedLogoListToArray(args[0].getList())).transpose());
    }
  }

  public static class MakeConstant extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_NUMBER, Syntax.TYPE_NUMBER, Syntax.TYPE_NUMBER},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      return new LogoMatrix(new Jama.Matrix(args[0].getIntValue(), args[1].getIntValue(), args[2].getDoubleValue()));
    }
  }

  public static class MakeIdentity extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_NUMBER},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      int size = args[0].getIntValue();
      return new LogoMatrix(Jama.Matrix.identity(size, size));
    }
  }

  public static class Copy extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      return new LogoMatrix(getMatrixFromArgument(args[0]).matrix.copy());
    }
  }

  public static class PrettyPrintText extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_STRING);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {

      double[][] dArray = getMatrixFromArgument(args[0]).matrix.getArray();
      int maxLen[] = new int[dArray[0].length];
      for (int j = 0; j < dArray[0].length; j++) {
        maxLen[j] = 0;
      }
      for (int i = 0; i < dArray.length; i++) {
        for (int j = 0; j < dArray[i].length; j++) {
          int len = org.nlogo.api.Dump.number(dArray[i][j]).length();
          if (len > maxLen[j]) {
            maxLen[j] = len;
          }
        }
      }

      StringBuilder buf = new StringBuilder();
      buf.append("[");
      for (int i = 0; i < dArray.length; i++) {
        if (i > 0) {
          buf.append(" ");
        }
        buf.append("[");
        for (int j = 0; j < dArray[i].length; j++) {
          if (j != 0) {
            buf.append(" ");
          }
          buf.append(" ");
          buf.append(String.format("%" + maxLen[j] + "s", org.nlogo.api.Dump.number(dArray[i][j])));
        }
        buf.append(" ]");
        if (i < dArray.length - 1) {
          buf.append("\n");
        }
      }
      buf.append("]");
      return buf.toString();
    }
  }


  public static class TimesScalar extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD, Syntax.TYPE_NUMBER},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      double d = args[1].getDoubleValue();
      return new LogoMatrix(mat.matrix.times(d));
    }
  }

  public static class Times extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD, Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      LogoMatrix mat2 = getMatrixFromArgument(args[1]);
      try {
        return new LogoMatrix(mat.matrix.times(mat2.matrix));
      } catch (IllegalArgumentException ex) {
        throw new ExtensionException(ex);
      }
    }
  }

  public static class TimesElementWise extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD, Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      LogoMatrix mat2 = getMatrixFromArgument(args[1]);
      try {
        return new LogoMatrix(mat.matrix.arrayTimes(mat2.matrix));
      } catch (IllegalArgumentException ex) {
        throw new ExtensionException(ex);
      }
    }
  }

  public static class PlusScalar extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD, Syntax.TYPE_NUMBER},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      double d = args[1].getDoubleValue();
      // no doubt could be more efficient
      return new LogoMatrix(mat.matrix.plus(
          new Jama.Matrix(mat.matrix.getRowDimension(), mat.matrix.getColumnDimension(), d)));
    }
  }

  public static class Plus extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD, Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      LogoMatrix mat2 = getMatrixFromArgument(args[1]);
      int numrows = mat.matrix.getRowDimension();
      int numcols = mat.matrix.getColumnDimension();
      int numrows2 = mat2.matrix.getRowDimension();
      int numcols2 = mat2.matrix.getColumnDimension();

      if (numrows != numrows2 || numcols != numcols2) {
        throw new org.nlogo.api.ExtensionException("Can not add matrices with different dimensions: "
            + numrows + "x" + numcols + " vs. " + numrows2 + "x"
            + numcols2);
      }

      return new LogoMatrix(mat.matrix.plus(mat2.matrix));
    }
  }

  public static class Det extends DefaultReporter {
    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_NUMBER);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      try {
        return new Double(mat.matrix.det());
      } catch (RuntimeException ex) {
        throw new ExtensionException(ex);
      }
    }
  }

  public static class Rank extends DefaultReporter {
    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_NUMBER);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      try {
        return new Double(mat.matrix.rank());
      } catch (RuntimeException ex) {
        throw new ExtensionException(ex);
      }
    }
  }

  public static class Cond extends DefaultReporter {
    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_NUMBER);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      try {
        return new Double(mat.matrix.cond());
      } catch (RuntimeException ex) {
        throw new ExtensionException(ex);
      }
    }
  }

  public static class Trace extends DefaultReporter {
    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_NUMBER);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      try {
        return new Double(mat.matrix.trace());
      } catch (RuntimeException ex) {
        throw new ExtensionException(ex);
      }
    }
  }


  public static class Inverse extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      try {
        return new LogoMatrix(mat.matrix.inverse());
      } catch (RuntimeException ex) {
        throw new ExtensionException(ex);
      }
    }
  }

  public static class Transpose extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      return new LogoMatrix(mat.matrix.transpose());
    }
  }

  public static class Submatrix extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD, Syntax.TYPE_NUMBER,
          Syntax.TYPE_NUMBER, Syntax.TYPE_NUMBER, Syntax.TYPE_NUMBER},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int r1 = args[1].getIntValue();
      int c1 = args[2].getIntValue();
      // NOTE: Jama.Matrix does the end row & col INCLUSIVE, but
      // we use EXCLUSIVE, to match NetLogo's SUBLIST, SUBSTRING, etc.
      int r2 = args[3].getIntValue();
      int c2 = args[4].getIntValue();
      int numRows = mat.matrix.getRowDimension();
      int numCols = mat.matrix.getColumnDimension();

      if (r1 < 0 || r1 >= numRows) {
        throw new org.nlogo.api.ExtensionException("Start row index ("
            + r1 + ") is invalid.  Should be between 0 and "
            + (numRows - 1) + " inclusive.");
      }
      if (c1 < 0 || c1 >= numCols) {
        throw new org.nlogo.api.ExtensionException("Start column index ("
            + c1 + ") is invalid.  Should be between 0 and "
            + (numCols - 1) + " inclusive.");
      }
      if (r2 < 1 || r2 > numRows) {
        throw new org.nlogo.api.ExtensionException("End row index ("
            + r2 + ") is invalid.  Should be between 1 and "
            + (numRows) + " inclusive.");
      }
      if (c2 < 1 || c2 > numCols) {
        throw new org.nlogo.api.ExtensionException("End column index ("
            + c2 + ") is invalid.  Should be between 1 and "
            + (numCols) + " inclusive.");
      }
      return new LogoMatrix(mat.matrix.getMatrix(r1, r2 - 1, c1, c2 - 1));
    }
  }

  public static class GetRow extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int rowIndex = args[1].getIntValue();
      int ncols = mat.matrix.getColumnDimension();
      if (rowIndex < 0 || rowIndex >= mat.matrix.getRowDimension()) {
        throw new org.nlogo.api.ExtensionException("(" + rowIndex + ") is not valid indices for a matrix with dimensions  "
            + mat.matrix.getRowDimension() + "x" + mat.matrix.getColumnDimension());
      }
      LogoMatrix rowArray = new LogoMatrix(mat.matrix.getMatrix(rowIndex, rowIndex, 0, ncols - 1));

      return convertArrayToSimpleLogoList(rowArray.matrix.getArray());
    }
  }

  public static class GetColumn extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD,
          Syntax.TYPE_NUMBER},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      int colIndex = args[1].getIntValue();
      int nrows = mat.matrix.getRowDimension();
      if (colIndex < 0 || colIndex >= mat.matrix.getColumnDimension()) {
        throw new org.nlogo.api.ExtensionException("(" + colIndex + ") is not valid indices for a matrix with dimensions  "
            + mat.matrix.getRowDimension() + "x" + mat.matrix.getColumnDimension());
      }
      LogoMatrix colArray = new LogoMatrix(mat.matrix.getMatrix(0, nrows - 1, colIndex, colIndex));

      return convertArrayToSimpleLogoList(colArray.matrix.getArray());
    }
  }

  public static class RealEigenvalues extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      LogoListBuilder retList = new LogoListBuilder();
      double[] eigenVals = mat.matrix.eig().getRealEigenvalues();
      for (double d : eigenVals) {
        retList.add(d);
      }
      return retList.toLogoList();
    }
  }

  public static class ImaginaryEigenvalues extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      LogoListBuilder retList = new LogoListBuilder();
      double[] eigenVals = mat.matrix.eig().getImagEigenvalues();
      for (double d : eigenVals) {
        retList.add(d);
      }
      return retList.toLogoList();
    }
  }

  public static class Eigenvectors extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      return new LogoMatrix(mat.matrix.eig().getV());
    }
  }

  public static class Solve extends DefaultReporter {

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD, Syntax.TYPE_WILDCARD},
          Syntax.TYPE_WILDCARD);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      LogoMatrix mat = getMatrixFromArgument(args[0]);
      LogoMatrix mat2 = getMatrixFromArgument(args[1]);
      try {
        return new LogoMatrix(mat.matrix.solve(mat2.matrix));
      } catch (RuntimeException ex) {
        throw new ExtensionException(ex);
      }
    }
  }

  public static class ForecastLinearTrend extends DefaultReporter {
    // This reporter takes a simple list of values, computes the regression line
    // describing them, and then returns a simple list where the first element
    // is the predicted value of the next point along the line, the second is
    // the regression constant, the third is the regression slope, and the fourth
    // is the regression R^2. The equation being fit is
    //     Y = constant + slope * t
    // where t is "time". If numObsv is the number of observations in the
    // input list, the forecast is thus given by
    //     forecast = constant + slope * (numObsv)
    // (As is normal in NetLogo, time begins with zero.)

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_LIST},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      // Y is the list of values to fit to a linear trend.
      Jama.Matrix Y = new Jama.Matrix(convertSimpleLogoListToArray(args[0].getList())).transpose();
      int numObsv = Y.getRowDimension();

      if (numObsv < 1) {
        throw new org.nlogo.api.ExtensionException(
            "The input list is empty.");
      }

      LogoListBuilder forecast = new LogoListBuilder();

      // Check for just one element in the input list.  No trend can be
      // computed so just return the single value as both the forecast and
      // trend constant, and set the slope and R^2 to zero.
      if (numObsv == 1) {
        forecast.add(Y.get(0, 0));
        forecast.add(Y.get(0, 0));
        forecast.add(0.0);
        forecast.add(0.0);
        return forecast.toLogoList();
      }

      Jama.Matrix X = new Jama.Matrix(numObsv, 2);
      for (int i = 0; i < numObsv; i++) {
        X.set(i, 0, 1.0);
        X.set(i, 1, (double) i);
      }
      Jama.Matrix A = X.solve(Y);

      // A is now a 2x1 matrix with the constant at 0,0 and the slope at 1,0.
      // Compute the forecast of Y for t = numObsv.
      double constant = A.get(0, 0);
      double slope = A.get(1, 0);
      double Yforecast = constant + slope * numObsv;

      // and the R^2 for the regression.  NOTE that if the total sum of
      // squares is zero, all the elements in the input list have the
      // same value.  The forecast and the constant will have been set to
      // that value above, the slope will have been set to zero,
      // and we set R^2 to unity.
      Jama.Matrix Ysum = new Jama.Matrix(1, numObsv, 1.0).times(Y);
      double Ybar = Ysum.get(0, 0) / numObsv;
      Jama.Matrix Ydiff = Y.minus(new Jama.Matrix(numObsv, 1, Ybar));
      double TotalSumSq = ((Ydiff.transpose()).times(Ydiff)).get(0, 0);
      Jama.Matrix Resid = (X.times(A)).minus(Y);
      double ResidSumSq = ((Resid.transpose()).times(Resid)).get(0, 0);
      double RSquared;
      if (TotalSumSq > 0) {
        RSquared = 1.0 - (ResidSumSq / TotalSumSq);
      } else {
        RSquared = 1.0;
      }

      forecast.add(Yforecast);
      forecast.add(constant);
      forecast.add(slope);
      forecast.add(RSquared);
      return forecast.toLogoList();
    }
  }

  public static class ForecastCompoundTrend extends DefaultReporter {
    // This reporter takes a simple list of values, finds the compound growth equation
    // (line) best describing them, and then returns a simple list where the first
    // element is the predicted value of the next point along the line, the second
    // is the function's constant, the third is one plus the function's growth rate,
    // and the fourth is the regression R^2. The form of the equation being fit is
    //     Y = constant * (1 + rate)^t
    // where t is "time" and rate is the compound growth rate.
    // If numObsv is the number of observations in the input list, the forecast is
    // thus given by
    //     forecast = constant * (1 + rate)^numObsv
    // (As is normal in NetLogo, time begins with zero.)
    // Note that (1 + rate) can be less than one, indicating a negative compound
    // growth rate.
    //
    // Because of the use of the log function, the input string can not contain
    // zero or negative values.  An error is thrown if they are encountered.

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_LIST},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      // Y is the list of values to fit to a compound growth trend.
      Jama.Matrix Yin = new Jama.Matrix(convertSimpleLogoListToArray(args[0].getList())).transpose();
      int numObsv = Yin.getRowDimension();

      if (numObsv < 1) {
        throw new org.nlogo.api.ExtensionException(
            "The input list is empty.");
      }
      for (int i = 0; i < numObsv; i++) {
        if (Yin.get(i, 0) <= 0.0) {
          throw new org.nlogo.api.ExtensionException(
              "Item " + i + " of the input list is zero or negative.");
        }
      }

      LogoListBuilder forecast = new LogoListBuilder();

      // Check for just one element in the input list.  No trend can be
      // computed so just return the single value as both the forecast and
      // trend constant, set (1 + rate) to unity. and set R^2 to zero.
      if (numObsv == 1) {
        forecast.add(Yin.get(0, 0));
        forecast.add(Yin.get(0, 0));
        forecast.add(1.0);
        forecast.add(0.0);
        return forecast.toLogoList();
      }

      Jama.Matrix Y = new Jama.Matrix(numObsv, 1);
      Jama.Matrix X = new Jama.Matrix(numObsv, 2);
      for (int i = 0; i < numObsv; i++) {
        Y.set(i, 0, Math.log(Yin.get(i, 0)));
        X.set(i, 0, 1.0);
        X.set(i, 1, (double) i);
      }
      Jama.Matrix A = X.solve(Y);

      // A is now a 2x1 matrix with the constant at 0,0 and the slope at 1,0.
      // Compute the forecast of Y for t = numObsv.
      double constant = Math.exp(A.get(0, 0));
      double onePlusRate = Math.exp(A.get(1, 0));
      double Yforecast = constant * Math.pow(onePlusRate, (double) numObsv);

      // and the R^2 for the regression.  NOTE that if the total sum of
      // squares is zero, all the elements in the input list have the
      // same value.  The forecast and the constant will have been set to
      // that value above, onePlusRate will have been set to unity,
      // and we set R^2 to unity.
      Jama.Matrix Ysum = new Jama.Matrix(1, numObsv, 1.0).times(Y);
      double Ybar = Ysum.get(0, 0) / numObsv;
      Jama.Matrix Ydiff = Y.minus(new Jama.Matrix(numObsv, 1, Ybar));
      double TotalSumSq = ((Ydiff.transpose()).times(Ydiff)).get(0, 0);
      Jama.Matrix Resid = (X.times(A)).minus(Y);
      double ResidSumSq = ((Resid.transpose()).times(Resid)).get(0, 0);
      double RSquared;
      if (TotalSumSq > 0) {
        RSquared = 1.0 - (ResidSumSq / TotalSumSq);
      } else {
        RSquared = 1.0;
      }

      forecast.add(Yforecast);
      forecast.add(constant);
      forecast.add(onePlusRate);
      forecast.add(RSquared);
      return forecast.toLogoList();
    }
  }

  public static class ForecastContinuousTrend extends DefaultReporter {
    // This reporter takes a simple list of values, fits a continous growth function
    // describing them, and then returns a simple list where the first element
    // is the predicted value of the next point along the growth line, the second
    // is the function's constant, the third is the function's growth rate, and the
    // fourth is the regression R^2. The form of the function being fit is
    //     Y = constant * e^(rate * t)
    // where t is "time" and rate is the continuous growth rate.
    // If numObsv is the number of observations in the input list, the forecast
    // is thus given by
    //     forecast = constant * e^(rate * numObsv)
    // (As is normal in NetLogo, time begins with zero.)
    // Note that rate can be negative, indicating a negative growth rate.
    // Note too that continuous growth is the continuous analog of compound growth
    // and the two procedures will usually give comparable results.
    //
    // Because of the use of the log function, the input string can not contain
    // zero or negative values.  An error is thrown if they are encountered.

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_LIST},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {
      // Y is the list of values to fit to an exponential trend.
      Jama.Matrix Yin = new Jama.Matrix(convertSimpleLogoListToArray(args[0].getList())).transpose();
      int numObsv = Yin.getRowDimension();

      if (numObsv < 1) {
        throw new org.nlogo.api.ExtensionException(
            "The input list is empty.");
      }
      for (int i = 0; i < numObsv; i++) {
        if (Yin.get(i, 0) <= 0.0) {
          throw new org.nlogo.api.ExtensionException(
              "Item " + i + " of the input list is zero or negative.");
        }
      }

      LogoListBuilder forecast = new LogoListBuilder();

      // Check for just one element in the input list.  No trend can be
      // computed so just return the single value as both the forecast and
      // trend constant, and set the growth rate and R^2 to zero.
      if (numObsv == 1) {
        forecast.add(Yin.get(0, 0));
        forecast.add(Yin.get(0, 0));
        forecast.add(0.0);
        forecast.add(0.0);
        return forecast.toLogoList();
      }

      Jama.Matrix Y = new Jama.Matrix(numObsv, 1);
      Jama.Matrix X = new Jama.Matrix(numObsv, 2);
      for (int i = 0; i < numObsv; i++) {
        Y.set(i, 0, Math.log(Yin.get(i, 0)));
        X.set(i, 0, 1.0);
        X.set(i, 1, (double) i);
      }
      Jama.Matrix A = X.solve(Y);

      // A is now a 2x1 matrix with the constant at 0,0 and the slope at 1,0.
      // Compute the forecast of Y for t = numObsv.
      double constant = Math.exp(A.get(0, 0));
      double rate = A.get(1, 0);
      double Yforecast = constant * Math.exp(rate * numObsv);

      // and the R^2 for the regression.  NOTE that if the total sum of
      // squares is zero, all the elements in the input list have the
      // same value.  The forecast and the constant will have been set to
      // that value above, the slope will have been set to zero,
      // and we set R^2 to unity.
      Jama.Matrix Ysum = new Jama.Matrix(1, numObsv, 1.0).times(Y);
      double Ybar = Ysum.get(0, 0) / numObsv;
      Jama.Matrix Ydiff = Y.minus(new Jama.Matrix(numObsv, 1, Ybar));
      double TotalSumSq = ((Ydiff.transpose()).times(Ydiff)).get(0, 0);
      Jama.Matrix Resid = (X.times(A)).minus(Y);
      double ResidSumSq = ((Resid.transpose()).times(Resid)).get(0, 0);
      double RSquared;
      if (TotalSumSq > 0) {
        RSquared = 1.0 - (ResidSumSq / TotalSumSq);
      } else {
        RSquared = 1.0;
      }

      forecast.add(Yforecast);
      forecast.add(constant);
      forecast.add(rate);
      forecast.add(RSquared);
      return forecast.toLogoList();
    }
  }

  public static class Regress extends DefaultReporter {
    // This reporter sets up and solves a linear OLS regression.
    // The input is LogoMatrix, with the first column being the observations on the
    // dependent variable and each subsequent column being the observations on the
    // n independent variables.  Each row is thus an observation of the dependent
    // variable followed by the corresponding observations of each independent variable.
    // Users may gather their data in either format and then need only form their input matrix
    // by column or by row, depending.
    // The output is a Logo nested list composed of two elements.  The first is the list of
    // the regression constant followed by the coefficients on each of the independent variables.
    // The second element is a list containing the R^2, the total sum of squares, and the
    // residual sum of squares.  More (or fewer) regression statistics could be relatively easily
    // calculated and included in the list.

    public Syntax getSyntax() {
      return Syntax.reporterSyntax(new int[]{Syntax.TYPE_WILDCARD},
          Syntax.TYPE_LIST);
    }

    public Object report(Argument args[], Context context)
        throws ExtensionException, LogoException {

      LogoMatrix mat = getMatrixFromArgument(args[0]);
      Jama.Matrix X = mat.matrix.copy();
      int numObsv = X.getRowDimension();
      int numVars = X.getColumnDimension() - 1;

      if (numVars >= numObsv) {
        throw new org.nlogo.api.ExtensionException(
            "The system is overdetermined.");
      }

      Jama.Matrix Y = new Jama.Matrix(numObsv, 1);
      for (int i = 0; i < numObsv; i++) {
        Y.set(i, 0, X.get(i, 0));
        X.set(i, 0, 1.0);
      }
      Jama.Matrix A = X.solve(Y);

      // A is now a numVars x 1 matrix of coefficients a(0) ... a(numVars).
      // Find R^2 for the regression.  Could eventually add more stats.
      Jama.Matrix Ysum = new Jama.Matrix(1, numObsv, 1.0).times(Y);
      double Ybar = Ysum.get(0, 0) / numObsv;
      Jama.Matrix Ydiff = Y.minus(new Jama.Matrix(numObsv, 1, Ybar));
      double TotalSumSq = ((Ydiff.transpose()).times(Ydiff)).get(0, 0);
      Jama.Matrix Resid = (X.times(A)).minus(Y);
      double ResidSumSq = ((Resid.transpose()).times(Resid)).get(0, 0);
      double RSquared = 1.0 - (ResidSumSq / TotalSumSq);

      LogoListBuilder stats = new LogoListBuilder();
      stats.add(RSquared);
      stats.add(TotalSumSq);
      stats.add(ResidSumSq);

      LogoList returnList = convertArrayToNestedLogoList(A.transpose().getArray());
      LogoListBuilder result = new LogoListBuilder();
      result.addAll(returnList);
      result.add(stats.toLogoList());
      return result.toLogoList();
    }
  }
}
