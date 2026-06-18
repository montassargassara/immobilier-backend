package com.immobilier.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class AIPriceRequest {

    @NotBlank  private String  city;
               private String  country           = "Tunisia";
    @NotBlank  private String  type;
    @NotNull @Positive private Double surface;
    @Min(0)    private Integer bedrooms          = 0;
    @Min(0)    private Integer bathrooms         = 1;
               private Boolean garage            = false;
               private Boolean piscine           = false;
               private Boolean jardin            = false;
               private Boolean meuble            = false;
    @Min(0)    private Integer etage             = 0;
    @Min(0)    private Integer parkingSpaces      = 0;
               private Integer anneeConstruction;
               private Boolean prochePlage       = false;
               private Boolean procheTransport   = false;
               private Boolean securite          = false;
               private Boolean climatisation     = false;

    public AIPriceRequest() {}

    public String  getCity()                         { return city; }
    public void    setCity(String v)                 { this.city = v; }
    public String  getCountry()                      { return country; }
    public void    setCountry(String v)              { this.country = v; }
    public String  getType()                         { return type; }
    public void    setType(String v)                 { this.type = v; }
    public Double  getSurface()                      { return surface; }
    public void    setSurface(Double v)              { this.surface = v; }
    public Integer getBedrooms()                     { return bedrooms; }
    public void    setBedrooms(Integer v)            { this.bedrooms = v; }
    public Integer getBathrooms()                    { return bathrooms; }
    public void    setBathrooms(Integer v)           { this.bathrooms = v; }
    public Boolean getGarage()                       { return garage; }
    public void    setGarage(Boolean v)              { this.garage = v; }
    public Boolean getPiscine()                      { return piscine; }
    public void    setPiscine(Boolean v)             { this.piscine = v; }
    public Boolean getJardin()                       { return jardin; }
    public void    setJardin(Boolean v)              { this.jardin = v; }
    public Boolean getMeuble()                       { return meuble; }
    public void    setMeuble(Boolean v)              { this.meuble = v; }
    public Integer getEtage()                        { return etage; }
    public void    setEtage(Integer v)               { this.etage = v; }
    public Integer getParkingSpaces()                { return parkingSpaces; }
    public void    setParkingSpaces(Integer v)       { this.parkingSpaces = v; }
    public Integer getAnneeConstruction()            { return anneeConstruction; }
    public void    setAnneeConstruction(Integer v)   { this.anneeConstruction = v; }
    public Boolean getProchePlage()                  { return prochePlage; }
    public void    setProchePlage(Boolean v)         { this.prochePlage = v; }
    public Boolean getProcheTransport()              { return procheTransport; }
    public void    setProcheTransport(Boolean v)     { this.procheTransport = v; }
    public Boolean getSecurite()                     { return securite; }
    public void    setSecurite(Boolean v)            { this.securite = v; }
    public Boolean getClimatisation()                { return climatisation; }
    public void    setClimatisation(Boolean v)       { this.climatisation = v; }
}
