package com.onurciner;

import android.util.Log;

import com.nutiteq.geometry.Geometry;
import com.nutiteq.geometry.Line;
import com.nutiteq.geometry.Point;
import com.nutiteq.geometry.Polygon;
import com.nutiteq.utils.Utils;
import com.nutiteq.utils.WkbRead;
import com.nutiteq.utils.WktWriter;
import com.onurciner.ohibernate.Blob;
import com.onurciner.ohibernate.Column;
import com.onurciner.ohibernate.Entity;
import com.onurciner.ohibernate.GeometryColumn;
import com.onurciner.ohibernate.Id;
import com.onurciner.ohibernate.NonColumn;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import jsqlite.Exception;
import jsqlite.Stmt;

/**
 * Created by Onur.Ciner on 7.11.2016.
 * VERSION 1.0.0
 */

public class OHibernate<K> {

    private K classType;

    public OHibernate setObj(K classType) {
        this.classType = classType;
        return this;
    }

    public OHibernate(Class<K> kClass) {
        try {
            this.classType = kClass.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        new OHibernateConfig().getConfig();
    }

    private Transactions transactions = new Process();

    private String tableName = "";

    private ArrayList<String> fields = new ArrayList<>();
    private ArrayList<String> fieldsValues = new ArrayList<>();
    private ArrayList<String> fieldsType = new ArrayList<>();

    private String id_fieldName = "";
    private String id_fieldType = "";

    private ArrayList<String> GeoColumnNames = new ArrayList<>();
    private ArrayList<Integer> GeoColumnSRids = new ArrayList<>();
    private ArrayList<GeometryColumn.GEO_TYPE> GeoColumnTypes = new ArrayList<>();

    private boolean idPrimeryKey = false;

    private void engine(boolean idStatus, boolean idRemove) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

        if (classType.getClass().isAnnotationPresent(Entity.class)) {

            Field[] all = classType.getClass().getDeclaredFields();
            for (Field field : all) {
                if (field.isAnnotationPresent(Id.class)) {
                    Id test = field.getAnnotation(Id.class);
                    if (test.NAME().equals("")) {
                        id_fieldName = field.getName();
                    } else {
                        id_fieldName = test.NAME();
                    }
                    //----
                    String typer = field.getType().getName().toString();
                    if (typer.contains(".")) {
                        String[] types = typer.split("\\.");
                        String type = types[types.length - 1];
                        id_fieldType = type;
                    } else {
                        id_fieldType = field.getType().getName().toString();
                    }
                    //----

                    if (test.PRIMERY_KEY_AUTOINCREMENT())
                        idPrimeryKey = true;
                }
            }

            Entity oent = classType.getClass().getAnnotation(Entity.class);
            if (!oent.TABLE_NAME().equals("")) {
                tableName = oent.TABLE_NAME();
            } else {
                tableName = classType.getClass().getSimpleName().toLowerCase().toString();
            }

            fields.clear();
            fieldsValues.clear();
            fieldsType.clear();

            Field[] allFields = classType.getClass().getDeclaredFields();
            for (Field field : allFields) {
                if (!field.getName().equals("serialVersionUID")) {
                    if (field.getType().equals(String.class) || field.getType().equals(Integer.class) || field.getType().equals(Long.class) || field.getType().equals(Double.class) || field.getType().equals(Float.class)
                            || field.getType().equals(int.class) || field.getType().equals(long.class) || field.getType().equals(double.class) || field.getType().equals(float.class) || field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {

                        Integer id = null;
                        if (field.isAnnotationPresent(Id.class)) {
                            Id test = field.getAnnotation(Id.class);
                            if (test.AUTO_ID() && idStatus) {
                                try {
                                    if (test.NEGATIVE()) {
                                        id = Integer.parseInt(getLastID());
                                        if (id < 0)
                                            id = id - 1;
                                        else {
                                            id = id + 1;
                                            id = -id;
                                        }
                                    } else
                                        id = Integer.parseInt(getLastID()) + 1;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            if (!test.NAME().equals(""))
                                fields.add(test.NAME());
                            else
                                fields.add(field.getName());
                        }

                        if (field.isAnnotationPresent(Column.class)) {
                            Column test = field.getAnnotation(Column.class);
                            if (!test.NAME().equals(""))
                                fields.add(test.NAME());
                            else
                                fields.add(field.getName());
                        } else if (!field.isAnnotationPresent(Id.class)) {
                            fields.add(field.getName());
                        }


                        Field fieldsa = classType.getClass().getDeclaredField(field.getName());
                        fieldsa.setAccessible(true);
                        Object value = fieldsa.get(classType);

                        if (id == null) {
                            if (value == null) {
                                value = "";
                                fieldsValues.add(value.toString());
                            } else
                                fieldsValues.add(value.toString());
                        } else {
                            fieldsValues.add(id + "");
                        }


                        String typer = field.getType().getName().toString();
                        if (typer.contains(".")) {
                            String[] types = typer.split("\\.");
                            String type = types[types.length - 1];
                            fieldsType.add(type);
                        } else {
                            fieldsType.add(field.getType().getName().toString());
                        }


                    }
                }

                if (field.isAnnotationPresent(GeometryColumn.class)) {
                    GeometryColumn test = field.getAnnotation(GeometryColumn.class);

                    if (!test.NAME().equals("")) {
                        fields.add(test.NAME());
                        GeoColumnNames.add(test.NAME());
                    } else {
                        fields.add(field.getName());
                        GeoColumnNames.add(field.getName());
                    }
                    Field fieldsa = classType.getClass().getDeclaredField(field.getName());
                    fieldsa.setAccessible(true);
                    Object value = fieldsa.get(classType);

                    String wktGeom = WktWriter.writeWkt((Geometry) value, getGeoType((Geometry) value));
                    fieldsValues.add("Transform(GeometryFromText('" + wktGeom + "'," + SDK_SRID + "), " + DEFAULT_SRID + ")");
                    //----
                    String typer = field.getType().getName().toString();
                    if (typer.contains(".")) {
                        String[] types = typer.split("\\.");
                        String type = types[types.length - 1];
                        fieldsType.add(type);
                    } else {
                        fieldsType.add(field.getType().getName().toString());
                    }
                    //----

                    GeoColumnSRids.add(test.SRID());
                    GeoColumnTypes.add(test.GEO_TYPE());
                }


                if (field.isAnnotationPresent(Blob.class)) {
                    Blob test = field.getAnnotation(Blob.class);

                    if (!test.NAME().equals("")) {
                        fields.add(test.NAME());
                    } else {
                        fields.add(field.getName());
                    }

                    Field fieldsa = classType.getClass().getDeclaredField(field.getName());
                    fieldsa.setAccessible(true);
                    Object value = fieldsa.get(classType);
                    if (test.IMAGE() || test.BYTES_TO_HEX())
                        fieldsValues.add(bytesToHex((byte[]) value));
                    else
                        fieldsValues.add(value.toString());
                    fieldsType.add("BLOB");

                }


                if (field.isAnnotationPresent(NonColumn.class)) {
                    if (field.getName().equals(fields.get(fields.size() - 1))) {
                        fields.remove(fields.size() - 1);
                        fieldsValues.remove(fieldsValues.size() - 1);

                        fieldsType.remove(fieldsType.size() - 1);
                    }
                }

                if (field.isAnnotationPresent(Column.class)) {
                    Column test = field.getAnnotation(Column.class);
                    if (test.DATETIME()) {
                        fieldsValues.remove(fieldsValues.size() - 1);
                        fieldsValues.add(getNowDateTime());
                    }
                }

                //insert işleminde  id silme işlemi gerekmekte aksi taktirde id Auto_id ise hata verir.
                //Ama Select işleminde id'ye ihitiyac var o tip durumlarda silinmemeli.
                if (idRemove) {
                    if (field.isAnnotationPresent(Id.class)) {
                        Id test = field.getAnnotation(Id.class);
                        if (!test.AUTO_ID()) {

                            if (!test.NAME().equals("")) {
                                if (field.getName().equals(id_fieldName)) {
                                    if (fields.contains(test.NAME())) {

                                        Field fieldsa = classType.getClass().getDeclaredField(field.getName());
                                        fieldsa.setAccessible(true);
                                        Object value = fieldsa.get(classType);

                                        fields.remove(test.NAME());
                                        fieldsValues.remove(fieldsValues.size() - 1);

                                        fieldsType.remove(fieldsType.size() - 1);
                                    }
                                }
                            } else {
                                if (fields.contains(field.getName())) {
                                    if (field.getName().equals(id_fieldName)) {
                                        Field fieldsa = classType.getClass().getDeclaredField(field.getName());
                                        fieldsa.setAccessible(true);
                                        Object value = fieldsa.get(classType);

                                        fields.remove(field.getName());
                                        fieldsValues.remove(fieldsValues.size() - 1);

                                        fieldsType.remove(fieldsType.size() - 1);
                                    }
                                }
                            }

                        }
                    }
                }

            }

            //TABLO İŞLEMLERİ
            Entity entity = classType.getClass().getAnnotation(Entity.class);
            if (entity.TABLE_OPERATION().equals(Entity.TABLE_OPERATION_TYPE.CREATE)) {
                tableCreate();
            } else if (entity.TABLE_OPERATION().equals(Entity.TABLE_OPERATION_TYPE.DROP_AND_CREATE)) {
                tableDelete();
                tableCreate();
            }


        }

        transactions.define(fieldsValues, fields, fieldsType, tableName, id_fieldName);
    }

    private void tableCreate() {
        if (!getTablesName().contains(tableName)) {
            //ID varsa onu başa almak için
            int teo = -1;
            for (int s = 0; s < fields.size(); s++) {
                if (fields.get(s).equals(id_fieldName)) {
                    fields.remove(s);
                    fields.add(0, id_fieldName);
                    teo = s;
                }
            }
            if (teo != -1) {
                String teoo = fieldsType.get(teo);
                fieldsType.remove(teo);
                fieldsType.add(0, teoo);

                String teooVal = fieldsValues.get(teo);
                fieldsValues.remove(teo);
                fieldsValues.add(0, teooVal);
            }
            //--------------------------------------------
            String keys = "";

            if (idPrimeryKey)
                if (id_fieldType.equals("Integer") || id_fieldType.equals("int"))
                    keys += id_fieldName + " INTEGER PRIMARY KEY AUTOINCREMENT, ";

            for (int i = 0; i < fields.size(); i++) {
                String type = "";
                if (fieldsType.get(i).equals("String") || fieldsType.get(i).equals("string")) {
                    type = "VARCHAR(255)";
                    keys += fields.get(i) + " " + type + ", ";
                } else if (fieldsType.get(i).equals("Integer") || fieldsType.get(i).equals("int")) {
                    type = "INTEGER";
                    keys += fields.get(i) + " " + type + ", ";
                } else if (fieldsType.get(i).equals("Geometry") || fieldsType.get(i).equals("GEOMETRY")
                        || fieldsType.get(i).equals("Point") || fieldsType.get(i).equals("POINT")
                        || fieldsType.get(i).equals("Line") || fieldsType.get(i).equals("LINE")
                        || fieldsType.get(i).equals("Polygon") || fieldsType.get(i).equals("POLYGON")) {

                } else {
                    type = fieldsType.get(i).toUpperCase();
                    keys += fields.get(i) + " " + type + ", ";
                }

            }
            keys = keys.substring(0, keys.length() - 2);


            String create = "CREATE TABLE " + tableName + " (" + keys + ")";

            try {
                OHibernateConfig.db.exec(create, null);
            } catch (Exception e) {
                e.printStackTrace();
            }

            createGeometryColumns();
        }
    }

    private void createGeometryColumns() {
        if (GeoColumnNames.size() > 0) {
            for (int i = 0; i < GeoColumnNames.size(); i++) {
                String create = "SELECT AddGeometryColumn('" + tableName + "', '" + GeoColumnNames.get(i) + "', " + GeoColumnSRids.get(i) + ", '" + GeoColumnTypes.get(i) + "', 'XY')";

                try {
                    OHibernateConfig.db.exec(create, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void tableDelete() {
        String drop = "DROP TABLE " + tableName + " ";

        try {
            OHibernateConfig.db.exec(drop, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ArrayList<String> getTablesName() {

        ArrayList<String> tableNames = new ArrayList<>();

        String sql = "SELECT name FROM sqlite_master WHERE type = 'table'";

        Stmt stmt = null;
        try {
            stmt = OHibernateConfig.db.prepare(sql);

            while (stmt.step()) {
                tableNames.add(stmt.column(0).toString());
            }
            stmt.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return tableNames;
    }

    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------

    // INSERT İŞLEMİ
    public String insert(K obj) throws Exception {
        this.classType = obj;

        try {
            engine(true, true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        String id = transactions.insert();

        return id;
    }

    /**
     * idStatus eğer true olursa otomatik olarak id ataması yapar. Default şeklide bu şekildedir. Eğer false ise o zaman id'ye dokunmaz.
     *
     * @param obj
     * @param idStatus
     * @return
     * @throws Exception
     */
    public String insert(K obj, boolean idStatus) throws Exception {
        this.classType = obj;

        try {
            engine(idStatus, true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        String id = transactions.insert();

        return id;
    }

    // UPDATE İŞLEMİ
    public void update(K obj) throws Exception {
        classType = obj;

        try {
            engine(false, false);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        transactions.update();

    }

    public void update() throws Exception {

        try {
            engine(false, false);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        transactions.update();
    }

    public void update(K obj, String key, String value) throws Exception {
        classType = obj;

        try {
            engine(false, true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        transactions.update(key, value);

    }

    // DELETE İŞLEMİ
    public void delete(K obj) throws Exception {
        classType = obj;

        try {
            engine(false, false);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        transactions.delete();

    }

    public void delete(K obj, String key, String value) throws Exception {
        classType = obj;

        try {
            engine(false, false);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        transactions.delete(key, value);
    }

    // SELECT İŞLEMİ
    public K select() throws Exception {

        try {
            engine(false, false);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        ArrayList<String> tempFields = getSelectTempFields();

        String keye = "";
        for (String field : tempFields) {
            keye += ", " + field + "";
        }
        String keys = keye.substring(2, keye.length());

        String key = "";
        String value = "";

        String sql = "";

        if (this.like != null) {

            if (this.where_key != null) {
                key = where_key;
                value = where_value;

                if (this.like == OHibernate.ENUM_LIKE.BASINA)
                    sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '%" + value + "'";
                if (this.like == OHibernate.ENUM_LIKE.SONUNA)
                    sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '" + value + "%'";
                if (this.like == OHibernate.ENUM_LIKE.HER_IKI_TARAFINA)
                    sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '%" + value + "%'";

            } else {
                sql = "SELECT " + keys + " FROM " + tableName + " ";
            }
        } else {
            if (this.where_key != null) {
                key = where_key;
                value = where_value;
                sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " = '" + value + "'";
            } else
                sql = "SELECT " + keys + " FROM " + tableName + " ";
        }

        Stmt stmt = OHibernateConfig.db.prepare(sql);
        while (stmt.step()) {
            K source = getInstance();

            for (int i = 0; i < fields.size(); i++) {
                try {
                    if (!tempFields.get(i).contains("HEX")) {

                        Field field = source.getClass().getDeclaredField(fields.get(i));
                        field.setAccessible(true);
                        if (stmt.column(i) != null && stmt.column(i).toString() != null &&
                                !stmt.column(i).toString().equals("NULL") && !stmt.column(i).toString().equals("null")) {
                            if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                                field.set(source, Integer.parseInt(stmt.column(i).toString()));
                            } else if (field.getType().equals(double.class) || field.getType().equals(Double.class)) {
                                field.set(source, Double.parseDouble(stmt.column(i).toString()));
                            } else if (field.getType().equals(float.class) || field.getType().equals(Float.class)) {
                                field.set(source, Float.parseFloat(stmt.column(i).toString()));
                            } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                                field.set(source, Long.parseLong(stmt.column(i).toString()));
                            } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                                field.set(source, Boolean.parseBoolean(stmt.column(i).toString()));
                            } else if (field.getType().equals(byte[].class) || field.getType().equals(Byte[].class)) {
                                field.set(source, stmt.column(i));
                            } else
                                field.set(source, stmt.column(i).toString());
                        }
                    } else {
                        Geometry[] geometries = WkbRead.readWkb(new ByteArrayInputStream(Utils.hexStringToByteArray(stmt.column(i).toString())), null);
                        Field field = source.getClass().getDeclaredField(fields.get(i));
                        field.setAccessible(true);
                        field.set(source, geometries[0]);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }
            return source;
        }
        stmt.close();

        return null;
    }

    public K select(String key, String value) throws Exception {

        try {
            engine(false, false);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        ArrayList<String> tempFields = getSelectTempFields();

        String keye = "";
        for (String field : tempFields) {
            keye += ", " + field + "";
        }
        String keys = keye.substring(2, keye.length());

        if (this.where_key != null) {
            key = where_key;
            value = where_value;
        }

        String sql = "";
        if (this.like != null) {
            if (this.like == OHibernate.ENUM_LIKE.BASINA)
                sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '%" + value + "'";
            if (this.like == OHibernate.ENUM_LIKE.SONUNA)
                sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '" + value + "%'";
            if (this.like == OHibernate.ENUM_LIKE.HER_IKI_TARAFINA)
                sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '%" + value + "%'";
        } else
            sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " = '" + value + "'";


        Stmt stmt = OHibernateConfig.db.prepare(sql);
        while (stmt.step()) {
            K source = getInstance();

            for (int i = 0; i < fields.size(); i++) {
                try {
                    if (!tempFields.get(i).contains("HEX")) {

                        Field field = source.getClass().getDeclaredField(fields.get(i));
                        field.setAccessible(true);
                        if (stmt.column(i) != null && stmt.column(i).toString() != null &&
                                !stmt.column(i).toString().equals("NULL") && !stmt.column(i).toString().equals("null")) {
                            if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                                field.set(source, Integer.parseInt(stmt.column(i).toString()));
                            } else if (field.getType().equals(double.class) || field.getType().equals(Double.class)) {
                                field.set(source, Double.parseDouble(stmt.column(i).toString()));
                            } else if (field.getType().equals(float.class) || field.getType().equals(Float.class)) {
                                field.set(source, Float.parseFloat(stmt.column(i).toString()));
                            } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                                field.set(source, Long.parseLong(stmt.column(i).toString()));
                            } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                                field.set(source, Boolean.parseBoolean(stmt.column(i).toString()));
                            } else if (field.getType().equals(byte[].class) || field.getType().equals(Byte[].class)) {
                                field.set(source, stmt.column(i));
                            } else
                                field.set(source, stmt.column(i).toString());
                        }
                    } else {
                        Geometry[] geometries = WkbRead.readWkb(new ByteArrayInputStream(Utils.hexStringToByteArray(stmt.column(i).toString())), null);
                        Field field = source.getClass().getDeclaredField(fields.get(i));
                        field.setAccessible(true);
                        field.set(source, geometries[0]);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }
            return source;
        }
        stmt.close();

        return null;
    }

    // SELECTALL İŞLEMİ
    public ArrayList<K> selectAll(String key, String value) throws Exception {

        try {
            engine(false, false);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        ArrayList<String> tempFields = getSelectTempFields();

        String keye = "";
        for (String field : tempFields) {
            keye += ", " + field + "";
        }
        String keys = keye.substring(2, keye.length());

        if (this.where_key != null) {
            key = where_key;
            value = where_value;
        }

        String sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + "='" + value + "'";

        if (this.like != null) {
            if (this.like == OHibernate.ENUM_LIKE.BASINA)
                sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '%" + value + "'";
            if (this.like == OHibernate.ENUM_LIKE.SONUNA)
                sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '" + value + "%'";
            if (this.like == OHibernate.ENUM_LIKE.HER_IKI_TARAFINA)
                sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '%" + value + "%'";
        }
        if (this.limit != null)
            sql += " LIMIT " + this.limit + "";


        ArrayList<K> sources = new ArrayList<K>();


        Stmt stmt = OHibernateConfig.db.prepare(sql);
        while (stmt.step()) {

            K source = getInstance();
            for (int i = 0; i < fields.size(); i++) {
                try {

                    if (!tempFields.get(i).contains("HEX")) {
                        Field field = source.getClass().getDeclaredField(fields.get(i));
                        field.setAccessible(true);
                        if (stmt.column(i) != null && stmt.column(i).toString() != null &&
                                !stmt.column(i).toString().equals("NULL") && !stmt.column(i).toString().equals("null")) {
                            if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                                field.set(source, Integer.parseInt(stmt.column(i).toString()));
                            } else if (field.getType().equals(double.class) || field.getType().equals(Double.class)) {
                                field.set(source, Double.parseDouble(stmt.column(i).toString()));
                            } else if (field.getType().equals(float.class) || field.getType().equals(Float.class)) {
                                field.set(source, Float.parseFloat(stmt.column(i).toString()));
                            } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                                field.set(source, Long.parseLong(stmt.column(i).toString()));
                            } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                                field.set(source, Boolean.parseBoolean(stmt.column(i).toString()));
                            } else if (field.getType().equals(byte[].class) || field.getType().equals(Byte[].class)) {
                                field.set(source, stmt.column(i));
                            } else
                                field.set(source, stmt.column(i).toString());
                        }
                    } else {
                        Geometry[] geometries = WkbRead.readWkb(new ByteArrayInputStream(Utils.hexStringToByteArray(stmt.column(i).toString())), null);
                        Field field = source.getClass().getDeclaredField(fields.get(i));
                        field.setAccessible(true);
                        field.set(source, geometries[0]);
                    }

                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }

            sources.add(source);
        }
        stmt.close();


        restart();

        return sources;
    }

    // SELECTALL İŞLEMİ
    public ArrayList<K> selectAll() throws Exception {
        try {
            engine(false, false);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (SecurityException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }

        ArrayList<String> tempFields = getSelectTempFields();

        String keye = "";
        for (String field : tempFields) {
            keye += ", " + field + "";
        }
        String keys = keye.substring(2, keye.length());


        String key = "";
        String value = "";
        String sql = "";


        if (this.like != null) {

            if (this.where_key != null) {
                key = where_key;
                value = where_value;

                if (this.like == OHibernate.ENUM_LIKE.BASINA)
                    sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '%" + value + "'";
                if (this.like == OHibernate.ENUM_LIKE.SONUNA)
                    sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '" + value + "%'";
                if (this.like == OHibernate.ENUM_LIKE.HER_IKI_TARAFINA)
                    sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " like '%" + value + "%'";

            } else {
                sql = "SELECT " + keys + " FROM " + tableName + " ";
            }
        } else {
            if (this.where_key != null) {
                key = where_key;
                value = where_value;
                sql = "SELECT " + keys + " FROM " + tableName + " WHERE " + key + " = '" + value + "'";
            } else
                sql = "SELECT " + keys + " FROM " + tableName + " ";
        }

        if (this.limit != null)
            sql += " LIMIT " + this.limit + "";

        ArrayList<K> sources = new ArrayList<K>();


        Stmt stmt = OHibernateConfig.db.prepare(sql);
        while (stmt.step()) {

            K source = getInstance();
            for (int i = 0; i < fields.size(); i++) {
                try {
                    if (!tempFields.get(i).contains("HEX")) {
                        Field field = source.getClass().getDeclaredField(fields.get(i));
                        field.setAccessible(true);
                        if (stmt.column(i) != null && stmt.column(i).toString() != null &&
                                !stmt.column(i).toString().equals("NULL") && !stmt.column(i).toString().equals("null")) {
                            if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                                field.set(source, Integer.parseInt(stmt.column(i).toString()));
                            } else if (field.getType().equals(double.class) || field.getType().equals(Double.class)) {
                                field.set(source, Double.parseDouble(stmt.column(i).toString()));
                            } else if (field.getType().equals(float.class) || field.getType().equals(Float.class)) {
                                field.set(source, Float.parseFloat(stmt.column(i).toString()));
                            } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                                field.set(source, Long.parseLong(stmt.column(i).toString()));
                            } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                                field.set(source, Boolean.parseBoolean(stmt.column(i).toString()));
                            } else if (field.getType().equals(byte[].class) || field.getType().equals(Byte[].class)) {
                                field.set(source, stmt.column(i));
                            } else
                                field.set(source, stmt.column(i).toString());
                        }
                    } else {
                        Geometry[] geometries = WkbRead.readWkb(new ByteArrayInputStream(Utils.hexStringToByteArray(stmt.column(i).toString())), null);
                        Field field = source.getClass().getDeclaredField(fields.get(i));
                        field.setAccessible(true);
                        field.set(source, geometries[0]);
                    }

                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }

            sources.add(source);
        }
        stmt.close();

        return sources;
    }

    //----------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------
    private final int DEFAULT_SRID = 4326;
    private final int SDK_SRID = 3857;

    private String getGeoType(Geometry geo) {
        if (geo instanceof Point)
            return "POINT";
        else if (geo instanceof Polygon)
            return "POLYGON";
        else if (geo instanceof Line)
            return "LINESTRING";
        else
            return null;
    }

    private Class<K> clazzOfT;

    private K getInstance() throws Exception {
        try {
            clazzOfT = (Class<K>) classType.getClass();
            return clazzOfT.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", e.getMessage());
        }
        return null;
    }

    private String getLastID() throws Exception {
        String one = "";
        String two = "";
        String sql = "SELECT MAX(" + id_fieldName + ") FROM " + tableName + " ";
        try {
            Stmt stmt = OHibernateConfig.db.prepare(sql);
            while (stmt.step()) {
                if (stmt.column(0) != null && stmt.column(0).toString() != null)
                    one = stmt.column(0).toString();
                else
                    one = "0";
            }
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", "getLastID() -> " + e.getMessage());
        }
        //-----------------------
        String sql2 = "SELECT MIN(" + id_fieldName + ") FROM " + tableName + " ";
        try {
            Stmt stmt = OHibernateConfig.db.prepare(sql2);
            while (stmt.step()) {
                if (stmt.column(0) != null && stmt.column(0).toString() != null)
                    two = stmt.column(0).toString();
                else
                    two = "0";
            }
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("OHibernate -> Error", "getLastID() -> " + e.getMessage());
        }
        //-----------------------
        if (!one.equals("") && !two.equals("") && !one.equals("null") && !two.equals("null")) {
            boolean oneB = false;
            boolean twoB = false;
            if (one.contains("-")) {
                one = one.replace("-", "");
                oneB = true;
            }
            if (two.contains("-")) {
                two = two.replace("-", "");
                twoB = true;
            }
            int Ione = (int) Double.parseDouble(one);
            int Itwo = (int) Double.parseDouble(two);

            if (Ione > Itwo)
                if (oneB)
                    return "-" + Ione;
                else
                    return Ione + "";
            else if (Itwo > Ione)
                if (twoB)
                    return "-" + Itwo;
                else
                    return Itwo + "";
            else {
                if (oneB)
                    return "-" + Ione;
                else
                    return Ione + "";
            }
        }
        return "0";
    }

    private String getNowDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTimeStamp = dateFormat.format(new Date());

        return currentTimeStamp;
    }

    private ArrayList<String> getSelectTempFields() {
        ArrayList<String> tempFields = (ArrayList<String>) fields.clone();

        Field[] allFields = classType.getClass().getDeclaredFields();
        for (Field field33 : allFields) {
            if (field33.isAnnotationPresent(Column.class)) {
                Column test = field33.getAnnotation(Column.class);
                if (!test.NAME().equals("")) {
                    if (tempFields.contains(test.NAME())) {
                        for (int a = 0; a < tempFields.size(); a++) {
                            if (tempFields.get(a).equals(test.NAME())) {

                                tempFields.remove(a);
                                tempFields.add(a, test.NAME() + " as " + field33.getName());

                                fields.remove(a);
                                fields.add(a, field33.getName());
                            }
                        }
                    }
                }
            }
            if (field33.isAnnotationPresent(Blob.class)) {
                Blob test = field33.getAnnotation(Blob.class);
                if (!test.NAME().equals("")) {
                    if (tempFields.contains(test.NAME())) {
                        for (int a = 0; a < tempFields.size(); a++) {
                            if (tempFields.get(a).equals(test.NAME())) {

                                tempFields.remove(a);
                                tempFields.add(a, test.NAME() + " as " + field33.getName());

                                fields.remove(a);
                                fields.add(a, field33.getName());
                            }
                        }
                    }
                }
            }
            if (field33.isAnnotationPresent(Id.class)) {
                Id test = field33.getAnnotation(Id.class);
                if (!test.NAME().equals("")) {
                    if (tempFields.contains(test.NAME())) {
                        for (int a = 0; a < tempFields.size(); a++) {
                            if (tempFields.get(a).equals(test.NAME())) {

                                tempFields.remove(a);
                                tempFields.add(a, test.NAME() + " as " + field33.getName());

                                fields.remove(a);
                                fields.add(a, field33.getName());
                            }
                        }
                    }
                }
            }
            if (field33.isAnnotationPresent(GeometryColumn.class)) {
                GeometryColumn test = field33.getAnnotation(GeometryColumn.class);
                if (!test.NAME().equals("")) {
                    if (tempFields.contains(test.NAME())) {
                        for (int a = 0; a < tempFields.size(); a++) {
                            if (tempFields.get(a).equals(test.NAME())) {

                                tempFields.remove(a);
                                tempFields.add(a, " HEX(AsBinary(Transform(" + test.NAME() + ",3857)))" + " as " + field33.getName());

                                fields.remove(a);
                                fields.add(a, field33.getName());
                            }
                        }
                    }
                } else {
                    if (tempFields.contains(field33.getName())) {
                        for (int a = 0; a < tempFields.size(); a++) {
                            if (tempFields.get(a).equals(field33.getName())) {

                                tempFields.remove(a);
                                tempFields.add(a, " HEX(AsBinary(Transform(" + field33.getName() + ",3857)))" + " as " + field33.getName());

                                fields.remove(a);
                                fields.add(a, field33.getName());
                            }
                        }
                    }
                }
            }

        }
        return tempFields;
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        if (bytes != null) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }
        return "";
    }
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------

    private void restart() {
        this.limit = null;
        this.like = null;
        this.where_key = null;
        this.where_value = null;
    }

    private Integer limit = null;

    public OHibernate limit(Integer limit) {
        this.limit = limit;
        return this;
    }

    private OHibernate.ENUM_LIKE like = null;

    public enum ENUM_LIKE {
        BASINA, SONUNA, HER_IKI_TARAFINA
    }

    public OHibernate like(OHibernate.ENUM_LIKE enum_like) {
        this.like = enum_like;
        return this;
    }

    private String where_key = null;
    private String where_value = null;

    public OHibernate where(String key, String value) {
        this.where_key = key;
        this.where_value = value;
        return this;
    }

    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
}